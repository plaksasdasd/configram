/*
This file is part of Telegram Desktop,
the official desktop application for the Telegram messaging service.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "intro/intro_start.h"

#include "lang/lang_keys.h"
#include "intro/intro_qr.h"
#include "intro/intro_phone.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/labels.h"
#include "core/application.h"
#include "core/file_utilities.h"
#include "main/main_auth_config.h"
#include "main/main_account.h"
#include "main/main_app_config.h"
#include "main/main_domain.h"
#include "data/data_peer_id.h"
#include "styles/style_intro.h"

#include <QtCore/QFile>

namespace Intro {
namespace details {
namespace {

[[nodiscard]] QString AuthConfigFileFilter() {
	return u"Telegram Config (*.tdesktop-config);;"_q
		+ FileDialog::AllFilesFilter();
}

} // namespace

StartWidget::StartWidget(
	QWidget *parent,
	not_null<Main::Account*> account,
	not_null<Data*> data)
: Step(parent, account, data, true)
, _authByConfig(this, tr::lng_auth_config(tr::now), st::introLink) {
	setMouseTracking(true);
	setTitleText(rpl::single(u"Telegram Desktop"_q));
	setDescriptionText(tr::lng_intro_about());
	setupAuthByConfig();
	show();
}

void StartWidget::submit() {
	account().destroyStaleAuthorizationKeys();
	goNext<QrWidget>();
}

rpl::producer<QString> StartWidget::nextButtonText() const {
	return tr::lng_start_msgs();
}

void StartWidget::setupAuthByConfig() {
	_authByConfig->show();
	rpl::combine(
		sizeValue(),
		_authByConfig->widthValue()
	) | rpl::start_with_next([=](QSize size, int width) {
		_authByConfig->moveToLeft(
			(size.width() - width) / 2,
			contentTop()
				+ st::introNextTop
				+ st::introNextButton.height
				+ st::introLinkTop);
	}, _authByConfig->lifetime());

	Lang::Updated(
	) | rpl::start_with_next([=] {
		_authByConfig->setText(tr::lng_auth_config(tr::now));
	}, _authByConfig->lifetime());

	_authByConfig->setClickedCallback([=] {
		const auto weak = base::make_weak(getData()->controller);
		FileDialog::GetOpenPath(
			Core::App().getFileDialogParent(),
			tr::lng_auth_config_import_title(tr::now),
			AuthConfigFileFilter(),
			[=](FileDialog::OpenResult &&result) {
				if (!weak) {
					return;
				}
				auto data = QByteArray();
				if (!result.paths.isEmpty()) {
					auto file = QFile(result.paths.front());
					if (!file.open(QIODevice::ReadOnly)) {
						weak->showToast(
							tr::lng_auth_config_error_read(tr::now));
						return;
					}
					data = file.readAll();
					file.close();
				} else {
					data = result.remoteContent;
				}
				if (data.isEmpty()) {
					weak->showToast(
						tr::lng_auth_config_error_read(tr::now));
					return;
				}
				const auto parsed = Main::ParseAuthConfig(std::move(data));
				if (!parsed) {
					weak->showToast(
						tr::lng_auth_config_error_invalid(tr::now));
					return;
				}
				auto &domain = Core::App().domain();
				const auto userId = UserId(parsed->userId);
				for (const auto &[index, account] : domain.accounts()) {
					if (account->sessionExists()
						&& account->mtp().environment()
							== parsed->environment
						&& account->session().userId() == userId) {
						domain.maybeActivate(account.get());
						weak->showToast(
							tr::lng_auth_config_error_exists(tr::now));
						return;
					}
				}
				if (domain.accounts().size() >= domain.maxAccounts()) {
					weak->showToast(
						tr::lng_auth_config_error_limit(tr::now));
					return;
				}
				const auto prepared = *parsed;
				weak->preventOrInvoke([=] {
					if (!prepared.appSettings.isEmpty()) {
						Core::App().settings().addFromSerialized(prepared.appSettings);
						Core::App().saveSettingsDelayed();
					}
					Core::App().domain().addActivatedWithAuthorization(
						prepared.environment,
						prepared.mtpAuthorization,
						prepared.sessionSettings);
				});
				weak->showToast(tr::lng_auth_config_imported(tr::now));
			});
	});
}

} // namespace details
} // namespace Intro
