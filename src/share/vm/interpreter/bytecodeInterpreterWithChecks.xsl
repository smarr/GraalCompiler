<?xml version="1.0"?> 
<!-- 
     Copyright 1997-2000 Sun Microsystems, Inc.  All Rights Reserved.
     DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

     This code is free software; you can redistribute it and/or modify it
     under the terms of the GNU General Public License version 2 only, as
     published by the Free Software Foundation.

     This code is distributed in the hope that it will be useful, but WITHOUT
     ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
     FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
     version 2 for more details (a copy is included in the LICENSE file that
     accompanied this code).

     You should have received a copy of the GNU General Public License version
     2 along with this work; if not, write to the Free Software Foundation,
     Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.

     Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
     CA 95054 USA or visit www.sun.com if you need additional information or
     have any questions.
-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:template match="processcode">
<xsl:text>
#define VM_JVMTI
#include "bytecodeInterpreter.cpp"
</xsl:text>
<xsl:text disable-output-escaping = "yes">

</xsl:text>

<xsl:output method="text" indent="no" omit-xml-declaration="yes"/>
</xsl:template>

</xsl:stylesheet>
