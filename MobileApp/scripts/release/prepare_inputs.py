#!/usr/bin/env python3
"""Prepare Firebase and release signing material on an ephemeral CI runner.

Signed builds consume one Base64 ZIP secret with keystore.properties and a .jks.
The secret is never printed or written to tracked paths.
"""
from pathlib import Path
import base64
import binascii
import io
import json
import os
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PROPERTIES_NAME = 'keystore.properties'
KEY_NAME = 'release-key.jks'
REQUIRED_FIELDS = ('storeFile', 'storePassword', 'keyAlias', 'keyPassword')


def decode_secret(name):
    encoded = os.environ.get(name, '')
    if not encoded:
        raise ValueError(f'Missing required secret: {name}')
    try:
        return base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError(f'Invalid Base64 in {name}') from exc


def read_properties(raw):
    # The bundle builder normalizes to simple properties entries; do not echo values.
    lines = raw.decode('utf-8').splitlines()
    result = {}
    for line in lines:
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        if '=' not in line:
            raise ValueError('Invalid signing bundle properties')
        key, value = line.split('=', 1)
        result[key.strip()] = value.strip()
    if any(not result.get(field) for field in REQUIRED_FIELDS):
        raise ValueError('Incomplete signing bundle properties')
    if result['storeFile'] != 'ci-signing/release-key.jks':
        raise ValueError('Unexpected signing bundle storeFile')
    return result


def prepare(signed=False):
    firebase = os.environ.get('FIREBASE_CONFIG_BASE64', '')
    if signed and not firebase:
        raise ValueError('Signed releases require FIREBASE_CONFIG_BASE64')
    if firebase:
        config_bytes = decode_secret('FIREBASE_CONFIG_BASE64')
        config = json.loads(config_bytes)
        clients = config.get('client', [])
        if not any(client.get('client_info', {}).get('android_client_info', {}).get('package_name') == 'com.karandaev.fieldfolio' for client in clients):
            raise ValueError('Firebase config does not contain the Fieldfolio application ID')
        target = ROOT / 'app/google-services.json'
        target.write_bytes(config_bytes)
        target.chmod(0o600)

    if signed:
        bundle_bytes = decode_secret('FIELDFOLIO_SIGNING_BUNDLE_BASE64')
        with zipfile.ZipFile(io.BytesIO(bundle_bytes)) as archive:
            if set(archive.namelist()) != {PROPERTIES_NAME, KEY_NAME}:
                raise ValueError('Signing bundle must contain exactly keystore.properties and release-key.jks')
            if any(info.file_size > 10 * 1024 * 1024 for info in archive.infolist()):
                raise ValueError('Signing bundle entry is too large')
            props = archive.read(PROPERTIES_NAME)
            key = archive.read(KEY_NAME)
        read_properties(props)
        if not key:
            raise ValueError('Signing keystore is empty')
        key_dir = ROOT / 'ci-signing'
        key_dir.mkdir(mode=0o700, exist_ok=True)
        key_target = key_dir / KEY_NAME
        key_target.write_bytes(key)
        key_target.chmod(0o600)
        props_target = ROOT / 'config' / PROPERTIES_NAME
        props_target.parent.mkdir(parents=True, exist_ok=True)
        props_target.write_bytes(props)
        props_target.chmod(0o600)


if __name__ == '__main__':
    prepare('--signed' in sys.argv)
