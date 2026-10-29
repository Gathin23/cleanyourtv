package com.gathin.tvhome;

/**
 * Answers android.intent.action.ALL_APPS, which a remote's apps button becomes on some sets (the Philips Google TV
 * TA5's 3x3 grid button, through org.droidtv.GlobalKey), with the Inputs menu, the way InputsRequestActivity answers
 * VIEW_INPUTS. Its own component so apps_key_inputs can switch it on without handle_view_inputs; disabled in the
 * manifest and enabled by MainActivity only while the key is set.
 */
public class AppsKeyInputsActivity extends InputsRequestActivity {
}
