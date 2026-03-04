/*
This file is part of Telegram Desktop,
the official desktop application for the Telegram messaging service.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

#include "mtproto/mtproto_dc_options.h"

#include <QtCore/QByteArray>
#include <optional>

namespace Main {

struct AuthConfigData {
	MTP::Environment environment = MTP::Environment::Production;
	QByteArray mtpAuthorization;
	QByteArray sessionSettings;
	QByteArray appSettings;
	quint64 userId = 0;
	MTP::DcId mainDcId = 0;
};

[[nodiscard]] QByteArray SerializeAuthConfig(
	MTP::Environment environment,
	const QByteArray &mtpAuthorization,
	const QByteArray &sessionSettings,
	const QByteArray &appSettings);
[[nodiscard]] std::optional<AuthConfigData> ParseAuthConfig(QByteArray data);

} // namespace Main
