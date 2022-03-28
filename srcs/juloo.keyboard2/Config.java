package juloo.keyboard2;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import java.util.Set;
import java.util.HashSet;

final class Config
{
  // From resources
  public final float marginTop;
  public final float keyPadding;

  public final float labelTextSize;
  public final float sublabelTextSize;

  // From preferences
  public int layout; // Or '-1' for the system defaults
  public int layoutNum; // Or '-1' for the system defaults
  private float swipe_dist_dp;
  public float swipe_dist_px;
  public boolean vibrateEnabled;
  public long vibrateDuration;
  public long longPressTimeout;
  public long longPressInterval;
  public float marginBottom;
  public float keyHeight;
  public float horizontalMargin;
  public float keyVerticalInterval;
  public float keyHorizontalInterval;
  public boolean preciseRepeat;
  public int lockable_modifiers;
  public float characterSize; // Ratio
  public int accents; // Values are R.values.pref_accents_v_*
  public int theme; // Values are R.style.*

  // Dynamically set
  public boolean shouldOfferSwitchingToNextInputMethod;
  public String actionLabel; // Might be 'null'
  public int actionId; // Meaningful only when 'actionLabel' isn't 'null'
  public boolean swapEnterActionKey; // Swap the "enter" and "action" keys
  public Set<String> extra_keys; // 'null' means all the keys

  public final IKeyEventHandler handler;

  private Config(Context context, IKeyEventHandler h)
  {
    Resources res = context.getResources();
    // static values
    marginTop = res.getDimension(R.dimen.margin_top);
    keyPadding = res.getDimension(R.dimen.key_padding);
    labelTextSize = res.getFloat(R.integer.label_text_size);
    sublabelTextSize = res.getFloat(R.integer.sublabel_text_size);
    // default values
    layout = -1;
    layoutNum = -1;
    vibrateEnabled = true;
    vibrateDuration = 20;
    longPressTimeout = 600;
    longPressInterval = 65;
    marginBottom = res.getDimension(R.dimen.margin_bottom);
    keyHeight = res.getDimension(R.dimen.key_height);
    horizontalMargin = res.getDimension(R.dimen.horizontal_margin);
    keyVerticalInterval = res.getDimension(R.dimen.key_vertical_interval);
    keyHorizontalInterval = res.getDimension(R.dimen.key_horizontal_interval);
    preciseRepeat = true;
    characterSize = 1.f;
    accents = 1;
    // from prefs
    refresh(context);
    // initialized later
    shouldOfferSwitchingToNextInputMethod = false;
    actionLabel = null;
    actionId = 0;
    swapEnterActionKey = false;
    extra_keys = null;
    handler = h;
  }

  /*
   ** Reload prefs
   */
  public void refresh(Context context)
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Resources res = context.getResources();
    DisplayMetrics dm = res.getDisplayMetrics();
    // The height of the keyboard is relative to the height of the screen. This
    // is not the actual size of the keyboard, which will be bigger if the
    // layout has a fifth row. 
    int keyboardHeightPercent;
    if (res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) // Landscape mode
    {
      keyboardHeightPercent = 55;
    }
    else
    {
      keyboardHeightPercent = prefs.getInt("keyboard_height", 35);
    }
    layout = layoutId_of_string(prefs.getString("layout", "system"));
    layoutNum = layoutNumId_of_string(prefs.getString("layout", "system"));
    swipe_dist_dp = Float.valueOf(prefs.getString("swipe_dist", "15"));
    swipe_dist_px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, swipe_dist_dp, dm);
    vibrateEnabled = prefs.getBoolean("vibrate_enabled", vibrateEnabled);
    vibrateDuration = prefs.getInt("vibrate_duration", (int)vibrateDuration);
    longPressTimeout = prefs.getInt("longpress_timeout", (int)longPressTimeout);
    longPressInterval = prefs.getInt("longpress_interval", (int)longPressInterval);
    marginBottom = getDipPref(dm, prefs, "margin_bottom", marginBottom);
    keyVerticalInterval = getDipPref(dm, prefs, "key_vertical_space", keyVerticalInterval);
    keyHorizontalInterval = getDipPref(dm, prefs, "key_horizontal_space", keyHorizontalInterval);
    // Do not substract keyVerticalInterval from keyHeight because this is done
    // during rendered.
    keyHeight = dm.heightPixels * keyboardHeightPercent / 100 / 4;
    horizontalMargin = getDipPref(dm, prefs, "horizontal_margin", horizontalMargin) + res.getDimension(R.dimen.extra_horizontal_margin);
    preciseRepeat = prefs.getBoolean("precise_repeat", preciseRepeat);
    lockable_modifiers =
      (prefs.getBoolean("lockable_shift", true) ? KeyValue.FLAG_SHIFT : 0)
      | (prefs.getBoolean("lockable_ctrl", false) ? KeyValue.FLAG_CTRL : 0)
      | (prefs.getBoolean("lockable_alt", false) ? KeyValue.FLAG_ALT : 0)
      | (prefs.getBoolean("lockable_fn", false) ? KeyValue.FLAG_FN : 0)
      | (prefs.getBoolean("lockable_meta", false) ? KeyValue.FLAG_META : 0)
      | (prefs.getBoolean("lockable_sup", false) ? KeyValue.FLAG_ACCENT_SUPERSCRIPT : 0)
      | (prefs.getBoolean("lockable_sub", false) ? KeyValue.FLAG_ACCENT_SUBSCRIPT : 0)
      | (prefs.getBoolean("lockable_box", false) ? KeyValue.FLAG_ACCENT_BOX : 0);
    characterSize = prefs.getFloat("character_size", characterSize);
    accents = Integer.valueOf(prefs.getString("accents", "1"));
    theme = getThemeId(res, prefs.getString("theme", ""));
  }

  /** Update the layout according to the configuration.
   *  - Remove the switching key if it isn't needed
   *  - Remove keys from other locales (not in 'extra_keys')
   *  - Replace the action key to show the right label
   *  - Swap the enter and action keys
   */
  public KeyboardData modify_layout(KeyboardData kw)
  {
    // Update the name to avoid caching in KeyModifier
    KeyValue action_key = (actionLabel == null) ? null :
      KeyValue.getKeyByName("action").withNameAndSymbol(actionLabel, actionLabel);
    return kw.replaceKeys(key -> {
      if (key == null)
        return null;
      switch (key.eventCode)
      {
        case KeyValue.EVENT_CHANGE_METHOD:
          return shouldOfferSwitchingToNextInputMethod ? key : null;
        case KeyEvent.KEYCODE_ENTER:
          return (swapEnterActionKey && action_key != null) ? action_key : key;
        case KeyValue.EVENT_ACTION:
          return (swapEnterActionKey && action_key != null) ?
            KeyValue.getKeyByName("enter") : action_key;
        default:
          if (key.flags != 0)
          {
            if ((key.flags & KeyValue.FLAG_LOCALIZED) != 0 &&
                extra_keys != null &&
                !extra_keys.contains(key.name))
              return null;
            if ((key.flags & lockable_modifiers) != 0)
              return key.withFlags(key.flags | KeyValue.FLAG_LOCK);
          }
          return key;
      }});
  }

  private float getDipPref(DisplayMetrics dm, SharedPreferences prefs, String pref_name, float def)
  {
    float value;
    try { value = prefs.getInt(pref_name, -1); }
    catch (Exception e) { value = prefs.getFloat(pref_name, -1f); }
    if (value < 0f)
      return (def);
    return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm));
  }

  private int getThemeId(Resources res, String theme_name)
  {
    switch (theme_name)
    {
      case "light": return R.style.Light;
      case "black": return R.style.Black;
      case "dark": return R.style.Dark;
      default:
      case "system":
        if (Build.VERSION.SDK_INT >= 8)
        {
          int night_mode = res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
          if ((night_mode & Configuration.UI_MODE_NIGHT_NO) != 0)
            return R.style.Light;
        }
        return R.style.Dark;
    }
  }

  public static int layoutId_of_string(String name)
  {
    switch (name)
    {
      case "azerty": return R.xml.azerty;
      case "bgph1": return R.xml.local_bgph1;
      case "dvorak": return R.xml.dvorak;
      case "qwerty_es": return R.xml.qwerty_es;
      case "qwerty_lv": return R.xml.qwerty_lv;
      case "qwerty_pt": return R.xml.qwerty_pt;
      case "qwerty": return R.xml.qwerty;
      case "qwerty2": return R.xml.qwerty2;
      case "qwerty3": return R.xml.qwerty3;
      case "qwerty4": return R.xml.qwerty4;
      case "qwerty5": return R.xml.qwerty5;
      case "qwerty6": return R.xml.qwerty6;
      case "qwerty7": return R.xml.qwerty7;
      case "qwerty8": return R.xml.qwerty8;
      case "qwerty9": return R.xml.qwerty9;
      case "qwerty_sv_se": return R.xml.qwerty_sv_se;
      case "qwertz": return R.xml.qwertz;
      case "ru_jcuken": return R.xml.local_ru_jcuken;
      case "system": default: return -1;
    }
  }

  public static int layoutNumId_of_string(String name)
  {
    switch (name)
    {
      case "azerty": return R.xml.numeric;
      case "bgph1": return R.xml.numeric;
      case "dvorak": return R.xml.numeric;
      case "qwerty_es": return R.xml.numeric;
      case "qwerty_lv": return R.xml.numeric;
      case "qwerty_pt": return R.xml.numeric;
      case "qwerty": return R.xml.numeric;
      case "qwerty2": return R.xml.numeric2;
      case "qwerty3": return R.xml.numeric3;
      case "qwerty4": return R.xml.numeric4;
      case "qwerty5": return R.xml.numeric5;
      case "qwerty6": return R.xml.numeric6;
      case "qwerty7": return R.xml.numeric7;
      case "qwerty8": return R.xml.numeric8;
      case "qwerty9": return R.xml.numeric9;
      case "qwerty_sv_se": return R.xml.numeric;
      case "qwertz": return R.xml.numeric;
      case "ru_jcuken": return R.xml.numeric;
      case "system": default: return -1;
    }
  }

  public static int themeId_of_string(String name)
  {
    switch (name)
    {
      case "light": return R.style.Light;
      case "black": return R.style.Black;
      default: case "dark": return R.style.Dark;
    }
  }

  private static Config _globalConfig = null;

  public static void initGlobalConfig(Context context, IKeyEventHandler handler)
  {
    _globalConfig = new Config(context, handler);
  }

  public static Config globalConfig()
  {
    return _globalConfig;
  }

  public static interface IKeyEventHandler
  {
    public void handleKeyUp(KeyValue value, int flags);
  }
}
