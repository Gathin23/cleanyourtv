#!/system/bin/sh
# Magisk post-fs-data / service script for ZTE ZXV10 B866F (oppen)
# Neutralizes com.control.device.receiver.ZteKeyInterceptorReceiver factory-reset watchdog
# and com.zte.framework.services.pm.PMSImpl.getBestActivity() HOME override.

until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 1
done

# 1. Disable ZTE ControlDevice factory-reset watchdog receivers at component level
pm disable com.control.device/.receiver.ZteKeyInterceptorReceiver >/dev/null 2>&1
pm disable com.control.device/.recevier.ZteKeyInterceptorReceiver >/dev/null 2>&1

# 2. Prevent PMSImpl.getBestActivity() from overriding PreferredActivities HOME resolution
settings put secure tv_user_setup_complete 0 >/dev/null 2>&1
cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity >/dev/null 2>&1

# 3. Re-assert after SetupWraith's delayed post-boot check (~45s)
sleep 45
pm disable com.control.device/.receiver.ZteKeyInterceptorReceiver >/dev/null 2>&1
pm disable com.control.device/.recevier.ZteKeyInterceptorReceiver >/dev/null 2>&1
settings put secure tv_user_setup_complete 0 >/dev/null 2>&1
cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity >/dev/null 2>&1
