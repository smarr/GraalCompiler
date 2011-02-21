/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

import java.util.*;
import java.io.File;

public class Util {
    static String join(String padder, Vector v) {
        return join(padder, v, false);
    }

    static String join(String padder, Vector v, boolean quoted) {
        StringBuffer sb = new StringBuffer();

        for (Iterator iter = v.iterator(); iter.hasNext(); ) {
            if (quoted) {
                sb.append('"');
            }
            sb.append((String)iter.next());
            if (quoted) {
                sb.append('"');
            }
            if (iter.hasNext()) sb.append(padder);
        }

        return sb.toString();
    }

     static String join(String padder, String v[]) {
        StringBuffer sb = new StringBuffer();

        for (int i=0; i<v.length; i++) {
            sb.append(v[i]);
            if (i < (v.length  - 1)) sb.append(padder);
        }

        return sb.toString();
    }



    static String prefixed_join(String padder, Vector v, boolean quoted) {
        StringBuffer sb = new StringBuffer();

        for (Iterator iter = v.iterator(); iter.hasNext(); ) {
            sb.append(padder);

            if (quoted) {
                sb.append('"');
            }
            sb.append((String)iter.next());
            if (quoted) {
                sb.append('"');
            }
        }

        return sb.toString();
    }


    static String normalize(String file) {
        return file.replace('\\', '/');
    }

    static String sep = File.separator;

    private static String _os;
    
    static String os() {
    	if( _os==null) {

        	for(Map.Entry<String, String> entry: System.getenv().entrySet())
        		if("PLATFORM_ARCH_MODEL".equals(entry.getKey().toUpperCase())) {
        			String archModel = entry.getValue();
        			if("x86_32".equals(archModel))
        				_os = "Win32";
        			else if("x86_64".equals(archModel))
        				_os = "x64";
        			else
        				throw new RuntimeException("Unsupported PLATFORM_ARCH_MODEL " + archModel);
        			return _os;
        		}
        	throw new RuntimeException("PLATFORM_ARCH_MODEL not specified");
    	}
    	return _os;
    }
    
}
