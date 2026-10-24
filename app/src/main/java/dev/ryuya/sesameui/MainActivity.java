package dev.ryuya.sesameui;

import android.app.LocaleManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textview.MaterialTextView;

/** Material 3 settings with a non-interactive, synthetic color preview. */
public final class MainActivity extends AppCompatActivity {
  private MaterialTextView status;
  private MaterialCardView preview;
  private LinearLayout previewBody;
  private SettingsTabs tabs;

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    LinearLayout root = vertical();
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          var bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    MaterialToolbar toolbar = new MaterialToolbar(this);
    toolbar.setTitle(R.string.app_name);
    toolbar
        .getMenu()
        .add(R.string.language)
        .setOnMenuItemClickListener(
            item -> {
              new MaterialAlertDialogBuilder(this)
                  .setTitle(R.string.language)
                  .setItems(
                      new String[] {"System / システム", "日本語", "English"},
                      (d, which) ->
                          getSystemService(LocaleManager.class)
                              .setApplicationLocales(
                                  LocaleList.forLanguageTags(
                                      which == 1 ? "ja" : which == 2 ? "en" : "")))
                  .show();
              return true;
            });
    root.addView(toolbar);
    tabs = new SettingsTabs(this, root, saved);
    appearance(tabs.page(R.string.display_tab));
    about(tabs.page(R.string.about_tab));
    tabs.restore();
    ViewCompat.requestApplyInsets(root);
  }

  private void appearance(LinearLayout page) {
    var prefs = ConfigProvider.settings(this);
    LinearLayout master = card(page, true);
    MaterialSwitch enabled = new MaterialSwitch(this);
    enabled.setId(R.id.module_enabled);
    enabled.setText(R.string.enabled);
    enabled.setMinHeight(dp(56));
    enabled.setChecked(prefs.getBoolean("enabled", true));
    master.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
    master.addView(text(R.string.enabled_note, false));
    enabled.setOnCheckedChangeListener(
        (button, value) -> {
          prefs.edit().putBoolean("enabled", value).apply();
          changed();
        });
    LinearLayout theme = card(page, false);
    theme.addView(text(R.string.appearance, true));
    RadioGroup choices = new RadioGroup(this);
    String mode = ThemeChoice.valid(prefs.getString("mode", ThemeChoice.SYSTEM));
    String[] values = {ThemeChoice.SYSTEM, ThemeChoice.DARK, ThemeChoice.LIGHT};
    int[] labels = {R.string.mode_system, R.string.mode_dark, R.string.mode_light};
    for (int i = 0; i < values.length; i++) {
      MaterialRadioButton choice = new MaterialRadioButton(this);
      choice.setId(View.generateViewId());
      choice.setTag(values[i]);
      choice.setText(labels[i]);
      choice.setMinHeight(dp(52));
      choices.addView(choice, new RadioGroup.LayoutParams(-1, -2));
      if (values[i].equals(mode)) choice.setChecked(true);
    }
    theme.addView(choices);
    choices.setOnCheckedChangeListener(
        (group, id) -> {
          View choice = group.findViewById(id);
          if (choice == null) return;
          prefs.edit().putString("mode", (String) choice.getTag()).apply();
          changed();
        });
    theme.addView(text(R.string.restart_note, false));
    LinearLayout sample = card(page, false);
    sample.addView(text(R.string.preview_title, true));
    preview = new MaterialCardView(this);
    preview.setRadius(dp(20));
    preview.setCardElevation(0);
    preview.setStrokeWidth(0);
    previewBody = vertical();
    previewBody.setPadding(dp(20), dp(22), dp(20), dp(22));
    preview.addView(previewBody);
    var size = new LinearLayout.LayoutParams(-1, -2);
    size.topMargin = dp(16);
    size.bottomMargin = dp(12);
    sample.addView(preview, size);
    sample.addView(text(R.string.preview_note, false));
    updatePreview();
    LinearLayout state = card(page, false);
    state.addView(text(R.string.status_title, true));
    status = text(R.string.status_waiting, false);
    status.setPadding(0, dp(12), 0, dp(12));
    state.addView(status);
    MaterialButton open = button(R.string.open_sesame);
    open.setOnClickListener(
        v -> {
          Intent intent = getPackageManager().getLaunchIntentForPackage(ConfigProvider.TARGET);
          if (intent != null) startActivity(intent);
          else Toast.makeText(this, R.string.not_installed, Toast.LENGTH_SHORT).show();
        });
    state.addView(open, new LinearLayout.LayoutParams(-1, -2));
    MaterialButton info = button(R.string.app_info);
    info.setOnClickListener(
        v ->
            startActivity(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + ConfigProvider.TARGET))));
    state.addView(info, new LinearLayout.LayoutParams(-1, -2));
  }

  private void changed() {
    ConfigProvider.settings(this).edit().putString("state", "waiting").apply();
    ConfigProvider.changed(this);
    updatePreview();
    refreshStatus();
  }

  private void updatePreview() {
    if (preview == null) return;
    var prefs = ConfigProvider.settings(this);
    boolean night =
        (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    boolean dark =
        ThemeChoice.dark(
            prefs.getBoolean("enabled", true), prefs.getString("mode", ThemeChoice.SYSTEM), night);
    preview.setCardBackgroundColor(dark ? DarkPalette.BACKGROUND : 0xfffafafa);
    previewBody.removeAllViews();
    MaterialTextView title = text(R.string.preview_name, true);
    title.setTextColor(dark ? DarkPalette.TEXT : 0xff172327);
    previewBody.addView(title);
    MaterialTextView subtitle = text(R.string.preview_detail, false);
    subtitle.setTextColor(dark ? DarkPalette.SECONDARY : 0xff5f6c72);
    subtitle.setPadding(0, dp(8), 0, dp(16));
    previewBody.addView(subtitle);
    View line = new View(this);
    line.setBackgroundColor(0xff28aeb1);
    previewBody.addView(line, new LinearLayout.LayoutParams(dp(36), dp(4)));
  }

  private void about(LinearLayout page) {
    LinearLayout setup = card(page, false);
    setup.addView(text(R.string.setup_title, true));
    setup.addView(text(R.string.setup_body, false));
    LinearLayout compatibility = card(page, false);
    compatibility.addView(text(R.string.compatibility, true));
    compatibility.addView(text(R.string.compatibility_body, false));
    LinearLayout guard = card(page, false);
    guard.addView(text(R.string.guard_title, true));
    guard.addView(text(R.string.guard_body, false));
    MaterialButton reset = button(R.string.guard_reset);
    reset.setOnClickListener(
        v -> {
          ConfigProvider.settings(this).edit().putLong("reset", System.currentTimeMillis()).apply();
          changed();
        });
    guard.addView(reset);
    LinearLayout app = card(page, false);
    MaterialTextView version = text(R.string.app_name, true);
    app.addView(version);
    MaterialTextView number = new MaterialTextView(this);
    number.setText(BuildConfig.VERSION_NAME);
    app.addView(number);
    app.addView(text(R.string.privacy_body, false));
    MaterialButton source = button(R.string.github);
    source.setOnClickListener(
        v ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://github.com/OneUI-Mods/SesameUITools"))));
    app.addView(source);
    MaterialButton licenses = button(R.string.legal_title);
    licenses.setOnClickListener(v -> startActivity(new Intent(this, LicensesActivity.class)));
    app.addView(licenses);
  }

  private void refreshStatus() {
    if (status == null) return;
    String state = ConfigProvider.settings(this).getString("state", "waiting");
    int label =
        switch (state) {
          case "dark" -> R.string.status_dark;
          case "light" -> R.string.status_light;
          case "disabled" -> R.string.status_disabled;
          case "blocked" -> R.string.status_blocked;
          case "unsupported" -> R.string.status_unsupported;
          case "error" -> R.string.status_error;
          default -> R.string.status_waiting;
        };
    status.setText(label);
  }

  protected void onResume() {
    super.onResume();
    refreshStatus();
  }

  protected void onSaveInstanceState(Bundle out) {
    tabs.save(out);
    super.onSaveInstanceState(out);
  }

  private LinearLayout vertical() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private LinearLayout card(LinearLayout parent, boolean primary) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(24));
    card.setCardElevation(0);
    card.setStrokeWidth(0);
    card.setCardBackgroundColor(
        MaterialColors.getColor(
            this,
            primary
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainerLow,
            "SesameUITools"));
    LinearLayout body = vertical();
    body.setPadding(dp(20), dp(16), dp(20), dp(16));
    card.addView(body);
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
    size.bottomMargin = dp(16);
    parent.addView(card, size);
    return body;
  }

  private MaterialTextView text(int label, boolean title) {
    MaterialTextView view = new MaterialTextView(this);
    view.setText(label);
    view.setTextAppearance(
        title
            ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            : com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    view.setLineSpacing(dp(3), 1);
    view.setPadding(0, dp(6), 0, dp(6));
    return view;
  }

  private MaterialButton button(int label) {
    MaterialButton view =
        new MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    view.setText(label);
    view.setMinHeight(dp(48));
    return view;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
