package org.apache.maven.plugins.pdf;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.kopitubruk.util.json.IndentPadding;
import org.kopitubruk.util.json.JSONConfig;
import org.kopitubruk.util.json.JSONParser;
import org.kopitubruk.util.json.JSONUtil;

/**
 * Helper to save then reload TOC content (to a json file), to be able to aggregate TOCs.
 * 
 * @author anthony-beurive
 * @since 1.5
 */
class TocFileHelper
{
    private static final String FILENAME = "toc.json";

    static void saveTOC( File workingDirectory, DocumentTOC toc, Locale locale )
        throws IOException
    {
        // FIXME: manage locales.
        JSONConfig jsonConfig = new JSONConfig();
        jsonConfig.setIndentPadding( new IndentPadding( "  ", "\n" ) );
        jsonConfig.addReflectClass( DocumentTOC.class );
        jsonConfig.addReflectClass( DocumentTOCItem.class );

        try ( Writer writer = WriterFactory.newWriter( getTocFile( workingDirectory ), "UTF-8" ) )
        {
            JSONUtil.toJSON( toc, jsonConfig, writer );
        }
    }

    static Map<String, Object> loadToc( File workingDirectory )
        throws IOException
    {
        try ( Reader reader = ReaderFactory.newReader( getTocFile( workingDirectory ), "UTF-8" ) )
        {
            return (Map) JSONParser.parseJSON( reader );
        }
    }

    private static File getTocFile( File workingDirectory )
    {
        return new File( workingDirectory, FILENAME );
    }
}
