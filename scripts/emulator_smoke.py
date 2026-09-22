#!/usr/bin/env python3
"""Exercise the real notification listener and vibrator on an emulator (never a physical device).

Requires installed debug APK and adb on PATH (or --adb). Temporarily configures Hamfist,
grants notification access, posts synthetic notifications, and restores preferences/access.
Does not claim to measure battery draw or physical haptic quality.
"""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("serial")
parser.add_argument("--adb", default="adb")
parser.add_argument("--wear", action="store_true")
parser.add_argument("--screen-off", action="store_true")
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.serial):
    parser.error("This test changes app settings and only accepts emulator serials.")

def adb(*cmd, data=None, check=True):
    p = subprocess.run([args.adb,"-s",args.serial,*cmd], input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    if check and p.returncode:
        raise RuntimeError(p.stderr.decode() or p.stdout.decode())
    return p.stdout

def shell(*cmd, **kwargs):
    return adb("shell",*cmd,**kwargs).decode()

component = "dev.hamfist/dev.hamfist.shared.NotificationRelay"
activity = "dev.hamfist/dev.hamfist." + ("wear" if args.wear else "phone") + ".MainActivity"
if not shell("pm","path","dev.hamfist").strip():
    raise SystemExit("Install the debug APK first. Gradle connected tests uninstall it when finished.")
old_preferences = adb("shell","run-as","dev.hamfist","cat","shared_prefs/hamfist.xml",check=False)
old_access = shell("settings","get","secure","enabled_notification_listeners")
already_allowed = component in old_access
tag = "hamfist-smoke-" + str(int(time.time()))

def configure(**overrides):
    config = dict(enabled=True,local=True,watch=False,content="TITLE",cooldown=0,silent=False,
                  respectSilent=False,saver=False,battery=0,cpm=100,auto=True)
    config.update(overrides)
    root = ET.Element("map")
    ET.SubElement(root,"string",name="settings").text = json.dumps(config)
    selected = ET.SubElement(root,"set",name="apps")
    ET.SubElement(selected,"string").text = "com.android.shell" if config.pop("selected", True) else "example.unselected"
    write_preferences(ET.tostring(root,encoding="utf-8"))

def write_preferences(data):
    shell("am","force-stop","dev.hamfist")
    shell("run-as","dev.hamfist","mkdir","-p","shared_prefs")
    adb("shell","run-as","dev.hamfist","sh","-c","'cat > shared_prefs/hamfist.xml'",data=data)
    shell("am","start","-n",activity)
    shell("cmd","notification","allow_listener",component)
    time.sleep(1)
    shell("input","keyevent","KEYCODE_HOME")
    if args.screen_off:
        shell("input","keyevent","KEYCODE_SLEEP")
    time.sleep(0.4)

def records():
    dump = shell("dumpsys","vibrator_manager")
    # API 34 and 36 use different vibration dump formats; each record contains opPkg.
    return {line.strip() for line in dump.splitlines() if "dev.hamfist" in line and ("NOTIFICATION" in line or "COMMUNICATION_REQUEST" in line)}

def post(title, body="synthetic-test", suffix=""):
    shell("cmd","notification","post","-t",title,tag+suffix,body)
    time.sleep(1)

try:
    configure()
    before=records(); post("E")
    added=records()-before
    assert added, "Selected notification did not reach the vibration service"
    assert any("finished" in r.lower() for r in added), "Vibration was requested but not completed: " + str(added)
    print("PASS selected app → background notification listener → completed vibration", flush=True)
    before=records(); post("E")
    assert records()==before, "Duplicate content vibrated again"
    print("PASS duplicate update suppressed",flush=True)
    before=records(); post("T",body="changed-message")
    assert records()-before, "Changed notification was suppressed"
    print("PASS changed message with same notification key delivered",flush=True)
    configure(selected=False)
    before=records(); post("E",suffix="-unselected")
    assert records()==before, "Unselected app vibrated"
    print("PASS unselected app ignored",flush=True)
    configure(enabled=False)
    before=records(); post("E",suffix="-paused")
    assert records()==before, "Paused app vibrated"
    print("PASS pause suppresses vibration",flush=True)
    configure(quiet=True,start=0,end=0)
    before=records(); post("E",suffix="-quiet")
    assert records()==before, "Quiet hours vibrated"
    print("PASS all-day quiet hours suppress vibration",flush=True)
    configure(cooldown=120)
    before=records(); post("E",suffix="-cooldown")
    assert records()-before, "First cooldown notification did not vibrate"
    before=records(); post("T",body="burst-update",suffix="-cooldown")
    assert records()==before, "Cooldown did not suppress burst"
    print("PASS per-app cooldown suppresses bursts",flush=True)
finally:
    write_preferences(old_preferences or b'<?xml version="1.0" encoding="utf-8"?><map/>')
    if not already_allowed:
        shell("cmd","notification","disallow_listener",component)
    print("Restored original Hamfist preferences and notification access",flush=True)
