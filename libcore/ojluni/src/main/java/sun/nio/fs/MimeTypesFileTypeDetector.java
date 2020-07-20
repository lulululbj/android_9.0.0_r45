/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.nio.fs;

import java.nio.file.Path;
import libcore.net.MimeUtils;

/**
 * File type detector that uses a file extension to look up its MIME type
 * based on a mime.types file.
 */

class MimeTypesFileTypeDetector extends AbstractFileTypeDetector {

    @Override
    protected String implProbeContentType(Path path) {
        Path fn = path.getFileName();
        if (fn == null)
            return null;  // no file name

        String ext = getExtension(fn.toString());
        if (ext.isEmpty())
            return null;  // no extension

        // Case-sensitive search
        String mimeType;
        do {
            mimeType = MimeUtils.guessMimeTypeFromExtension(ext);
            if (mimeType == null)
                ext = getExtension(ext);
        } while (mimeType == null && !ext.isEmpty());

        return mimeType;
    }

    // Get the extension of a file name.
    private static String getExtension(String name) {
        String ext = "";
        if (name != null && !name.isEmpty()) {
            int dot = name.indexOf('.');
            if ((dot >= 0) && (dot < name.length() - 1)) {
                ext = name.substring(dot + 1);
            }
        }
        return ext;
    }
}
