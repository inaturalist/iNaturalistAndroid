#
# Python 3 script for validating and importing translations from a crowdin build
# zip.
#

from glob import glob
from subprocess import run, PIPE
import sys
import os
import re
import shutil
import tempfile
import xml.etree.ElementTree as ET
from optparse import OptionParser

# https://developer.android.com/reference/java/util/Formatter#syntax
ANDROID_FORMAT_PATTERN = re.compile(r"%((?P<argument_index>\d+)\$)?(?P<flags>[\-\#\+\s0\,\(])?(?P<width>\d+)?(?P<precision>\.\d+)?(?P<conversion>[bhscdoxefgat%n])")

def call_cmd(*args, **kwargs):
  run(args, **kwargs)

def extless_basename(path):
  if os.path.isfile(path):
    return os.path.splitext(os.path.basename(path))[0]
  return os.path.split(path)[-1]

def import_crowdin_for_android(zip_path, options=None):
  if zip_path == __file__:
    zip_path = sys.argv[1]
  dir_path = os.path.join(tempfile.mkdtemp(), extless_basename(zip_path))
  call_cmd("unzip", zip_path, "-d", dir_path)
  for path in glob("{}/*".format(dir_path)):
    if options.locale and options.locale not in path:
      continue
    crowdin_locale = extless_basename(path)
    locale, *sublocale = crowdin_locale.split("-")
    sublocale = None if len(sublocale) == 0 else sublocale[0]
    android_locale = locale
    if sublocale:
      android_locale = "{}-r{}".format(locale, sublocale)
    android_dir_path = os.path.join( "iNaturalist", "src", "main", "res", "values-{}".format(android_locale))
    if not os.path.isdir(android_dir_path):
      if options.verbose:
        print("\tCreating {}".format(android_dir_path))
      os.makedirs(android_dir_path, exist_ok=True)
    src = os.path.join(path, "Android", "strings.xml")
    dst = os.path.join(android_dir_path, "strings.xml")
    if options.verbose:
      print("\tCopying {} to {}".format(src, dst))
    shutil.copyfile(src, dst)

def validate_android_translations(options=None):
  # Build the English reference dicts
  en_tree = ET.parse("iNaturalist/src/main/res/values/strings.xml")
  en_strings = {}
  en_string_arrays = {}
  for node in en_tree.findall("string"):
    en_strings[node.get("name")] = node.text
  for node in en_tree.findall("string-array"):
    en_string_arrays[node.get("name")] = [child.text for child in node]
  # Compile validation errors
  errors = {}
  warnings = {}
  for path in glob("iNaturalist/src/main/res/values-*/strings.xml"):
    if options.locale and options.locale not in path:
      continue
    if options.verbose:
      print("Checking {}".format(path))
    errors[path] = {}
    warnings[path] = {}
    tree = ET.parse(path)
    for node in tree.findall("string"):
      key = node.get("name")
      if options.key and options.key not in key:
        continue
      if key not in en_strings:
        continue
      en_string = en_strings[key]
      if options.debug:
        print("\tkey:         {}".format(key))
        print("\ten_string:   {}".format(en_string))
        print("\ttranslation: {}".format(node.text))
      # Ensure all variables in the English string are in the translation
      for match in re.finditer(ANDROID_FORMAT_PATTERN, en_string):
        translation_mismatch = None
        number_of_usages = 0
        for tm in re.finditer(ANDROID_FORMAT_PATTERN, node.text):
          indexes_match = tm.group("argument_index") == match.group("argument_index")
          conversions_match = tm.group("conversion") == match.group("conversion")
          if indexes_match:
            number_of_usages += 1
          if indexes_match and not conversions_match:
            translation_mismatch = tm
        # if variable missing in node.text:
        if translation_mismatch or number_of_usages == 0:
          if key not in errors[path]:
            errors[path][key] = []
          errors[path][key].append("Missing variable {}".format(match.group(0)))
          if options.debug:
            print("\t\t{}".format(errors[path][key][-1]))
        # if too many usages
        if number_of_usages > 1:
          if key not in warnings[path]:
            warnings[path][key] = []
          warnings[path][key].append("Too many usages of {}".format(match.group(0)))
          if options.debug:
            print("\t\t{}".format(warnings[path][key][-1]))
      # Warn about variables that are no longer present in English
      if node.text:
        en_indexes = [m.group("argument_index") for m in re.finditer(ANDROID_FORMAT_PATTERN, en_string)]
        for match in re.finditer(ANDROID_FORMAT_PATTERN, node.text):
          if match.group("argument_index") not in en_indexes:
            if key not in errors[path]:
              errors[path][key] = []
            errors[path][key].append("Extraneous variable {}".format(tm.group(0)))
            if options.debug:
              print("\t\t{}".format(errors[path][key][-1]))
      # if unescaped single quotes
      if node.text and re.search(r"[^\\]'", node.text):
        if key not in errors[path]:
          errors[path][key] = []
        errors[path][key].append("Unescaped single quote")
        if options.debug:
          print("\t\t{}".format(errors[path][key][-1]))

  # Print validation errors
  for path in glob("iNaturalist/src/main/res/values-*/strings.xml"):
    if options.locale and options.locale not in path:
      continue
    keys = list(errors[path].keys()) + list(warnings[path].keys())
    keys = [k for k in keys if k != None]
    keys = set(keys)
    print(path)
    if len(keys) == 0:
      print("\tNo problems")
      continue
    for key in keys:
      print("\t{}".format(key))
      if key in errors[path]:
        for error in errors[path][key]:
          print("\t\tERROR: {}".format(error))
      if key in warnings[path]:
        for warning in warnings[path][key]:
          print("\t\tWarning: {}".format(warning))

def find_unused_keys(options=None):
  en_tree = ET.parse("iNaturalist/src/main/res/values/strings.xml")
  keys = set()
  for node in en_tree.findall("string"):
    keys.add(node.get("name"))
  for node in en_tree.findall("string-array"):
    keys.add(node.get("name"))
  print("Looking for unused keys...")
  unused = []
  for key in sorted(keys):
    if options.debug:
      print("Checking {}".format(key))
    else:
      print("\rChecking {0:{1}}".format(key, 100), end="\r", flush=True)
    result = run(["egrep", "-r", "(R\.(string|array)\.|@(string|array)\/){}".format(key), "./iNaturalist/src/main/"],
      stdout=PIPE)
    lines = result.stdout.decode('utf-8').strip().split("\n")
    if not lines[0]:
      lines = []
    if options.debug:
      print("\t{} uses".format(len(lines)))
    if len(lines) == 0:
      unused.append(key)
  print("\r\n{} potentially unused keys (check for dynamic key generation):".format(len(unused)), flush=True)
  for key in unused:
    print(key)

def main():
  parser = OptionParser("Usage: %prog [options]")
  parser.description = """
    Script for validating and updating translations from crowdin. Default
    action is to validate the existing translations.

    USAGE

      python3 crowdin.py -f path/to/crowdin.zip -v

    Note: Python 3 required.
  """.strip()
  parser.add_option("-v", "--verbose", action="store_true", dest="verbose")
  parser.add_option("-d", "--debug", action="store_true", dest="debug")
  parser.add_option("-f", "--file", action="store", type="string", dest="zip_path",
    help="Path to crowdin export zip file. This will replace all local versions of these files with the crowdin versions.")
  parser.add_option("-l", "--locale", action="store", dest="locale", help="Only import and/or validate this locale")
  parser.add_option("-k", "--key", action="store", dest="key", help="Only validate keys containing this substring")
  parser.add_option("-u", "--find-unused", action="store_true", dest="find_unused", help="Show keys that don't seem to be used any more")
  (options, args) = parser.parse_args()
  if options.zip_path:
    import_crowdin_for_android(options.zip_path, options)
  validate_android_translations(options)
  if options.find_unused:
    print("\n\n")
    find_unused_keys(options)

if __name__ == "__main__":
  main()
