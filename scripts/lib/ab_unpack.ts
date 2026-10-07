//
//   Calendar Notifications Plus
//   Copyright (C) 2026 William Harris (wharris+cnplus@upscalews.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//

/**
 * Pack/unpack the plain `adb backup` .ab format.
 *
 * An unencrypted .ab is a 24-byte text header followed by a
 * zlib-deflated tar stream:
 *
 *   ANDROID BACKUP\n5\n1\nnone\n<deflated tar>
 *
 * Line 1: magic. Line 2: format version. Line 3: compression flag
 * (1 = compressed). Line 4: encryption ("none"). This tool only handles
 * "none" encryption -- an encrypted .ab needs the user's password to
 * derive the key, and the merge script is a local, unencrypted flow.
 */

import * as fs from 'fs'
import * as zlib from 'zlib'

const HEADER = Buffer.from('ANDROID BACKUP\n5\n1\nnone\n', 'utf8')

export function unpackAb(abPath: string): Buffer {
  const raw = fs.readFileSync(abPath)
  if (!raw.subarray(0, HEADER.length).equals(HEADER)) {
    const preview = raw.subarray(0, 24).toString('utf8').replace(/\n/g, '\\n')
    throw new Error(
      `Not a plain unencrypted .ab: expected "${HEADER.toString('utf8').replace(/\n/g, '\\n')}", got "${preview}"`
    )
  }
  return zlib.inflateSync(raw.subarray(HEADER.length))
}

export function packAb(tarBytes: Buffer, outPath: string): void {
  const deflated = zlib.deflateSync(tarBytes)
  fs.writeFileSync(outPath, Buffer.concat([HEADER, deflated]))
}
