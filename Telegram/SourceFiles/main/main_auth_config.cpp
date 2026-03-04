/*
This file is part of Telegram Desktop,
the official desktop application for the Telegram messaging service.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "main/main_auth_config.h"

#include "storage/serialize_common.h"

#include <QtCore/QDataStream>

namespace Main {
namespace {

constexpr auto kAuthConfigMagic = 0x54444143U; // "TDAC"
constexpr auto kAuthConfigVersion = 2; // version 2 includes app settings
constexpr auto kWideIdsTag = ~quint64(0);
constexpr auto kMaxAuthKeys = 64;

[[nodiscard]] bool ParseMtpAuthorization(
		const QByteArray &serialized,
		quint64 &userId,
		MTP::DcId &mainDcId) {
	if (serialized.isEmpty()) {
		return false;
	}
	QDataStream stream(serialized);
	stream.setVersion(QDataStream::Qt_5_1);

	const auto legacyUserId = Serialize::read<qint32>(stream);
	const auto legacyMainDcId = Serialize::read<qint32>(stream);
	if (stream.status() != QDataStream::Ok) {
		return false;
	}
	if (((quint64(legacyUserId) << 32) | quint64(legacyMainDcId))
		== kWideIdsTag) {
		userId = Serialize::read<quint64>(stream);
		mainDcId = Serialize::read<qint32>(stream);
	} else {
		userId = quint64(legacyUserId);
		mainDcId = legacyMainDcId;
	}
	if (stream.status() != QDataStream::Ok || !userId) {
		return false;
	}

	const auto readKeys = [&] {
		const auto count = Serialize::read<qint32>(stream);
		if (stream.status() != QDataStream::Ok
			|| count < 0
			|| count > kMaxAuthKeys) {
			return false;
		}
		for (auto i = 0; i != count; ++i) {
			Serialize::read<qint32>(stream);
			Serialize::read<MTP::AuthKey::Data>(stream);
			if (stream.status() != QDataStream::Ok) {
				return false;
			}
		}
		return true;
	};
	if (!readKeys() || !readKeys()) {
		return false;
	}
	return (stream.status() == QDataStream::Ok);
}

} // namespace

QByteArray SerializeAuthConfig(
		MTP::Environment environment,
		const QByteArray &mtpAuthorization,
		const QByteArray &sessionSettings,
		const QByteArray &appSettings) {
	auto stream = Serialize::ByteArrayWriter();
	stream
		<< quint32(kAuthConfigMagic)
		<< qint32(kAuthConfigVersion)
		<< qint32(environment)
		<< mtpAuthorization
		<< sessionSettings
		<< appSettings;
	return std::move(stream).result();
}

std::optional<AuthConfigData> ParseAuthConfig(QByteArray data) {
	auto stream = Serialize::ByteArrayReader(std::move(data));
	auto magic = quint32();
	auto version = qint32();
	auto environment = qint32();
	auto mtpAuthorization = QByteArray();
	auto sessionSettings = QByteArray();
	auto appSettings = QByteArray();
	stream >> magic >> version >> environment >> mtpAuthorization >> sessionSettings;
	if (version >= 2) {
		stream >> appSettings;
	}
	if (!stream.ok()
		|| magic != kAuthConfigMagic
		|| version > kAuthConfigVersion
		|| version < 1) {
		return std::nullopt;
	}
	const auto env = (environment == qint32(MTP::Environment::Test))
		? MTP::Environment::Test
		: (environment == qint32(MTP::Environment::Production))
		? MTP::Environment::Production
		: std::optional<MTP::Environment>();
	if (!env) {
		return std::nullopt;
	}
	auto userId = quint64();
	auto mainDcId = MTP::DcId();
	if (!ParseMtpAuthorization(mtpAuthorization, userId, mainDcId)) {
		return std::nullopt;
	}
	return AuthConfigData{
		.environment = *env,
		.mtpAuthorization = std::move(mtpAuthorization),
		.sessionSettings = std::move(sessionSettings),
		.appSettings = std::move(appSettings),
		.userId = userId,
		.mainDcId = mainDcId,
	};
}

} // namespace Main
