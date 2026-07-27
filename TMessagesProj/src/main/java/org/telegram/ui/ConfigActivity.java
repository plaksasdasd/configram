package org.telegram.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AppearanceConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Экран «Конфиг»: создание конфига внешнего вида из текущей персонализации
 * и вставка конфига из буфера обмена с применением настроек локально.
 */
public class ConfigActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int createConfigRow;
    private int pasteConfigRow;
    private int shadowRow;
    private int infoRow;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.Config));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        rowCount = 0;
        createConfigRow = rowCount++;
        pasteConfigRow = rowCount++;
        shadowRow = rowCount++;
        infoRow = rowCount++;

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position == createConfigRow) {
                createConfig();
            } else if (position == pasteConfigRow) {
                pasteConfigFromClipboard(this, currentAccount);
            }
        });

        return fragmentView;
    }

    private void createConfig() {
        if (getParentActivity() == null) {
            return;
        }
        String config = AppearanceConfig.exportConfig(currentAccount);
        if (config == null) {
            return;
        }
        AndroidUtilities.addToClipboard(config);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ConfigCreatedTitle));
        builder.setMessage(LocaleController.getString(R.string.ConfigCreatedText));
        builder.setPositiveButton(LocaleController.getString(R.string.ShareFile), (dialog, which) -> shareConfig(config));
        builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    private void shareConfig(String config) {
        try {
            File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "telegram-config.txt");
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(AndroidUtilities.getStringBytes(config));
            stream.close();

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Вставить конфиг из буфера обмена и применить его локально.
     * Используется и на экране настроек, и на экране входа.
     */
    public static void pasteConfigFromClipboard(final BaseFragment fragment, final int currentAccount) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        String text = null;
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) fragment.getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence clipText = clip.getItemAt(0).coerceToText(fragment.getParentActivity());
                if (clipText != null) {
                    text = clipText.toString();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (!AppearanceConfig.isConfig(text)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.Config));
            builder.setMessage(LocaleController.getString(R.string.ConfigInvalid));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            fragment.showDialog(builder.create());
            return;
        }

        final String config = text;
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.Config));
        builder.setMessage(LocaleController.getString(R.string.ConfigApplyConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.Apply), (dialog, which) -> {
            boolean applied = AppearanceConfig.importConfig(currentAccount, config);
            AlertDialog.Builder result = new AlertDialog.Builder(fragment.getParentActivity());
            result.setTitle(LocaleController.getString(R.string.Config));
            result.setMessage(LocaleController.getString(applied ? R.string.ConfigApplied : R.string.ConfigInvalid));
            result.setPositiveButton(LocaleController.getString(R.string.OK), null);
            fragment.showDialog(result.create());
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new ShadowSectionCell(context);
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case 0:
                default:
                    view = new TextCell(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    if (position == createConfigRow) {
                        textCell.setText(LocaleController.getString(R.string.CreateConfig), true);
                    } else if (position == pasteConfigRow) {
                        textCell.setText(LocaleController.getString(R.string.PasteConfig), false);
                    }
                    break;
                case 2:
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    infoCell.setText(LocaleController.getString(R.string.ConfigAboutInfo));
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == shadowRow) {
                return 1;
            } else if (position == infoRow) {
                return 2;
            }
            return 0;
        }
    }
}
