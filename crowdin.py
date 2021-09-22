#
# Python 3 script for validating and importing translations from a crowdin
# build zip.
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


class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


# https://developer.android.com/reference/java/util/Formatter#syntax
ANDROID_FORMAT_PATTERN = re.compile(
    r"%((?P<argument_index>\d+)\$)?(?P<flags>[\-\#\+\s0\,\(])?(?P<width>\d+)?(?P<precision>\.\d+)?(?P<conversion>[bhscdoxefgat%n])"  # noqa: E501
)
DATE_FORMAT_KEYS = [
    "date_short_this_year",
    "date_short",
    "time_short_24_hour",
    "time_short_12_hour"
]
# These are locales where Crowdin doesn't actually have the correct Android
# locale code, or where we need to copy files to multiple locations to
# accommodate different contexts, e.g. the same Hebrew translations for several
# locale codes
CROWDIN_TO_ANDROID_LOCALES = {
    # Assume Spanish in Spain is the default Spanish
    "es-ES": ["es", "es-rES"],
    # Long and complicated history of locale changes for Hebrew, so we just
    # copy this to all possibilities
    "he": ["iw", "iw-rIL", "he-rIL"],
    "id": ["id", "in", "in-rID"],
    # Assume Portuguese in Portugal is the default Portuguese
    "pt-PT": ["pt", "pt-rPT"],
    "sv-SE": ["sv"],
    "sr-Cyrl": ["sr"]
}


def call_cmd(*args, **kwargs):
    run(args, **kwargs)


def extless_basename(path):
    if os.path.isfile(path):
        return os.path.splitext(os.path.basename(path))[0]
    return os.path.split(path)[-1]


def copy_to_android_locale(src, android_locale, options={}):
    android_dir_path = os.path.join(
        "iNaturalist", "src", "main", "res", "values-{}".format(android_locale)
    )
    if not os.path.isdir(android_dir_path):
        if options.verbose:
            print("\tCreating {}".format(android_dir_path))
        os.makedirs(android_dir_path, exist_ok=True)
    dst = os.path.join(android_dir_path, "strings.xml")
    if options.verbose:
        print("\tCopying {} to {}".format(src, dst))
    shutil.copyfile(src, dst)


def import_crowdin_for_android(zip_path, options={}):
    if zip_path == __file__:
        zip_path = sys.argv[1]
    dir_path = os.path.join(tempfile.mkdtemp(), extless_basename(zip_path))
    call_cmd("unzip", zip_path, "-d", dir_path)
    for path in sorted(glob("{}/*".format(dir_path))):
        if options.verbose:
            print(f"Considering {path}")
        if options.locale and options.locale not in path:
            continue
        crowdin_locale = extless_basename(path)
        locale, *sublocale = crowdin_locale.split("-")
        sublocale = None if len(sublocale) == 0 else sublocale[0]
        android_locale = locale
        if sublocale:
            android_locale = "{}-r{}".format(locale, sublocale)
        src = os.path.join(path, "Android", "strings.xml")
        copy_to_android_locale(src, android_locale, options)
        # Copy Hebrew file to the old locale codes that some modern Androids
        # still use
        for src_crowdin_locale in CROWDIN_TO_ANDROID_LOCALES:
            if options.verbose:
                print(f"Considering if {src_crowdin_locale} matches {crowdin_locale}...")
            if crowdin_locale == src_crowdin_locale:
                for dest_android_locale in CROWDIN_TO_ANDROID_LOCALES[src_crowdin_locale]:
                    if options.verbose:
                        print(f"Copying to {dest_android_locale}...")
                    copy_to_android_locale(src, dest_android_locale, options)
        # if locale == "he":
        #     for new_android_locale in ["iw", "iw-rIL", "he-rIL"]:
        #         copy_to_android_locale(src, new_android_locale, options)
        # elif android_locale == "sv-rSE":
        #     copy_to_android_locale(src, "sv", options)


def validate_translation(path, key, text, en_string, errors, warnings,
                         options={}):
    if options.debug:
        print("\tkey:                 {}".format(key))
        print("\ten_string:     {}".format(en_string))
        print("\ttranslation: {}".format(text))
    # Ensure all variables in the English string are in the translation
    for match in re.finditer(ANDROID_FORMAT_PATTERN, en_string):
        translation_mismatch = None
        number_of_usages = 0
        for tm in re.finditer(ANDROID_FORMAT_PATTERN, text):
            indexes_match = (
                tm.group("argument_index") == match.group("argument_index")
            )
            conversions_match = (
                tm.group("conversion") == match.group("conversion")
            )
            if indexes_match:
                number_of_usages += 1
            if indexes_match and not conversions_match:
                translation_mismatch = tm
        # if variable missing in node.text:
        if translation_mismatch or number_of_usages == 0:
            if key not in errors[path]:
                errors[path][key] = []
            errors[path][key].append(
                "Missing variable {}".format(match.group(0))
            )
            if options.debug:
                print("\t\t{}".format(errors[path][key][-1]))
        # if too many usages
        if number_of_usages > 1:
            if key not in warnings[path]:
                warnings[path][key] = []
            warnings[path][key].append(
                "Too many usages of {}".format(match.group(0))
            )
            if options.debug:
                print("\t\t{}".format(warnings[path][key][-1]))
    # Warn about variables that are no longer present in English
    if text:
        en_indexes = [
            m.group("argument_index")
            for m in re.finditer(ANDROID_FORMAT_PATTERN, en_string)
        ]
        for match in re.finditer(ANDROID_FORMAT_PATTERN, text):
            if match.group("argument_index") not in en_indexes:
                if key not in errors[path]:
                    errors[path][key] = []
                errors[path][key].append("Extraneous variable {}".format(
                    match.group("argument_index"))
                )
                if options.debug:
                    print("\t\t{}".format(errors[path][key][-1]))
    # if unescaped single quotes
    if text and re.search(r"[^\\]'", text):
        if key not in errors[path]:
            errors[path][key] = []
        errors[path][key].append("Unescaped single quote")
        if options.debug:
            print("\t\t{}".format(errors[path][key][-1]))
    if text and key in DATE_FORMAT_KEYS:
        without_escaped_text = re.sub(r"'.+?'", "", text)
        potentially_formatted = re.sub(r"[^\w]", "", without_escaped_text)
        bad_characters = set(
            re.findall(
                r"[^GyYMLwWDdFEuaHkKhmsSzZX]",
                potentially_formatted)
            )
        if bad_characters and len(bad_characters) > 0:
            if key not in errors[path]:
                errors[path][key] = []
            errors[path][key].append(
                f"Invalid date format characters: {bad_characters}")
            if options.debug:
                print("\t\t{}".format(errors[path][key][-1]))


def en_translations():
    # Build the English reference dicts
    en_tree = ET.parse("iNaturalist/src/main/res/values/strings.xml")
    en_strings = {}
    en_string_arrays = {}
    en_string_plurals = {}
    for node in en_tree.findall("string"):
        en_strings[node.get("name")] = node.text
    for node in en_tree.findall("string-array"):
        en_string_arrays[node.get("name")] = [child.text for child in node]
    for node in en_tree.findall("plurals"):
        en_string_plurals[node.get("name")] = {
            child.get("quantity"): child.text for child in node
        }
    return (en_strings, en_string_arrays, en_string_plurals)


def validate_android_translations(options={}):
    # Build the English reference dicts
    en_strings, en_string_arrays, en_string_plurals = en_translations()
    # Compile validation errors
    errors = {}
    warnings = {}
    progress_counts = {}
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
            text = node.text
            if options.key and options.key not in key:
                continue
            if key not in en_strings:
                continue
            en_string = en_strings[key]
            validate_translation(
                path, key, text, en_string, errors, warnings, options
            )
            if text != en_strings[key]:
                progress_counts[path] = (
                    progress_counts[path] + 1 if path in progress_counts else 0
                )
        for string_array in tree.findall("string-array"):
            key = string_array.get("name")
            if options.key and options.key not in key:
                continue
            if key not in en_string_arrays:
                continue
            for idx, item in enumerate(string_array):
                en_string = en_string_arrays[key][idx]
                text = item.text
                if text:
                    validate_translation(
                        path,
                        f"{key}.{idx}",
                        text,
                        en_string,
                        errors,
                        warnings,
                        options
                    )
                    if text != en_string:
                        progress_counts[path] = (
                            progress_counts[path] + 1 if path in progress_counts else 0  # noqa: E501
                        )
        for plural in tree.findall("plurals"):
            key = plural.get("name")
            if options.key and options.key not in key:
                continue
            if key not in en_string_plurals:
                continue
            for item in plural:
                item_key = item.get("quantity")
                en_string = en_string_plurals[key].get(item_key)
                text = item.text
                if text and en_string:
                    validate_translation(
                        path,
                        "{}.{}".format(key, item_key),
                        text,
                        en_string,
                        errors,
                        warnings,
                        options
                    )
                if text != en_string:
                    progress_counts[path] = (
                        progress_counts[path] + 1 if path in progress_counts else 0  # noqa: E501
                    )

    # Print validation errors & progress
    num_keys = (
        len(en_strings)
        + sum([len(a) for a in en_string_arrays])
        + sum([len(a) for a in en_string_plurals])
    )
    untranslated = []
    for path in glob("iNaturalist/src/main/res/values-*/strings.xml"):
        if options.locale and options.locale not in path:
            continue
        keys = list(errors[path].keys()) + list(warnings[path].keys())
        keys = [k for k in keys if k is not None]
        keys = set(keys)
        print(f"{bcolors.BOLD}{path}{bcolors.ENDC}")
        if path in progress_counts:
            percent = round(progress_counts[path] / num_keys * 100)
        else:
            percent = 0
        if percent == 0:
            print(f"\t{bcolors.FAIL}{percent}% translated{bcolors.ENDC}")
            untranslated.append(path)
        elif percent < 66:
            print(f"\t{bcolors.WARNING}{percent}% translated{bcolors.ENDC}")
        else:
            print(f"\t{bcolors.OKGREEN}{percent}% translated{bcolors.ENDC}")
        if len(keys) == 0:
            print(f"\t{bcolors.OKGREEN}No problems{bcolors.ENDC}")
            continue
        for key in keys:
            print("\t{}".format(key))
            if key in errors[path]:
                for error in errors[path][key]:
                    print(f"\t\t{bcolors.FAIL}ERROR: {error}{bcolors.ENDC}")
            if key in warnings[path]:
                for warning in warnings[path][key]:
                    print(f"\t\t{bcolors.WARNING}Warning: {warning}{bcolors.ENDC}")  # noqa: E501
    if len(untranslated) > 0:
        print("\nIf you want to delete untranslated files...\n")
        for path in untranslated:
            print(f"rm {path}")


def find_unused_keys(options={}):
    en_tree = ET.parse("iNaturalist/src/main/res/values/strings.xml")
    keys = set()
    for node in en_tree.findall("string"):
        keys.add(node.get("name"))
    for node in en_tree.findall("string-array"):
        keys.add(node.get("name"))
    for node in en_tree.findall("plurals"):
        keys.add(node.get("name"))
    print("Looking for unused keys...")
    unused = []
    for key in sorted(keys):
        if options.debug:
            print("Checking {}".format(key))
        else:
            print("\rChecking {0:{1}}".format(key, 100), end="\r", flush=True)
        result = run(
            [
                "egrep",
                "-r",
                "(R\.(string|array|plurals)\.|@(string|array|plurals)\/){}".format(key),
                "./iNaturalist/src/main/"
            ],
            stdout=PIPE
        )
        lines = result.stdout.decode('utf-8').strip().split("\n")
        if not lines[0]:
            lines = []
        if options.debug:
            print("\t{} uses".format(len(lines)))
        if len(lines) == 0:
            unused.append(key)
    print(
        "\r\n{} potentially unused keys (check for dynamic key generation):".format(len(unused)),  # noqa: E501
        flush=True
    )
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
    parser.add_option(
        "-f", "--file", action="store", type="string", dest="zip_path",
        help=(
            "Path to crowdin export zip file. This will replace all local "
            "versions of these files with the crowdin versions."
        )
    )
    parser.add_option("-l", "--locale", action="store", dest="locale", help=(
        "Only import and/or validate this locale"
    ))
    parser.add_option("-k", "--key", action="store", dest="key", help=(
        "Only validate keys containing this substring"
    ))
    parser.add_option(
        "-u", "--find-unused", action="store_true",
        dest="find_unused",
        help="Show keys that don't seem to be used any more"
    )
    (options, args) = parser.parse_args()
    if options.zip_path:
        import_crowdin_for_android(options.zip_path, options)
    validate_android_translations(options)
    if options.find_unused:
        print("\n\n")
        find_unused_keys(options)


if __name__ == "__main__":
    main()
