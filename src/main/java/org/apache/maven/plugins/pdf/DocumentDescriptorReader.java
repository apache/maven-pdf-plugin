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
import java.io.StringReader;

import java.util.Properties;

import org.apache.maven.doxia.document.DocumentModel;
import org.apache.maven.doxia.document.io.xpp3.DocumentXpp3Reader;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.ObjectBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import java.util.Locale;

/**
 * Read and filter a DocumentModel from a document descriptor file.
 *
 * @author ltheussl
 */
public class DocumentDescriptorReader
{
    /** A MavenProject to extract additional info. */
    private final MavenProject project;

    /** Used to log the interpolated document descriptor. */
    private final Log log;

    private final Locale locale;

    /**
     * Constructor.
     */
    public DocumentDescriptorReader()
    {
        this( null, null, null );
    }

    /**
     * Constructor.
     *
     * @param project may be null.
     */
    public DocumentDescriptorReader( final MavenProject project )
    {
        this( project, null, null );
    }

    /**
     * Constructor.
     *
     * @param project may be null.
     * @param log may be null.
     */
    public DocumentDescriptorReader( final MavenProject project, final Log log, final Locale locale )
    {
        this.project = project;
        this.log = log;
        this.locale = locale;
    }

    /**
     * Read and filter the <code>docDescriptor</code> file.
     *
     * @param docDescriptor not null, corresponding to non-localized descriptor file.
     * @return a DocumentModel instance.
     * @throws XmlPullParserException if an error occurs during parsing.
     * @throws IOException if an error occurs during reading.
     */
    public DocumentModel readAndFilterDocumentDescriptor( File docDescriptor )
        throws XmlPullParserException, IOException
    {
        if ( locale != null )
        {
            String descriptorFilename = docDescriptor.getName();
            String localized = FileUtils.removeExtension( descriptorFilename ) + '_' + locale.getLanguage() + '.'
                + FileUtils.getExtension( descriptorFilename );
            File localizedDocDescriptor = new File( docDescriptor.getParentFile(), localized );

            if ( localizedDocDescriptor.exists() )
            {
                docDescriptor = localizedDocDescriptor;
            }
        }

        try
        {
            // System properties
            final Properties filterProperties = System.getProperties();
            // Project properties
            if ( project != null && project.getProperties() != null )
            {
                filterProperties.putAll( project.getProperties() );
            }

            final Interpolator interpolator = new RegexBasedInterpolator();
            interpolator.addValueSource( new MapBasedValueSource( filterProperties ) );
            interpolator.addValueSource( new EnvarBasedValueSource() );
            interpolator.addValueSource( new ObjectBasedValueSource( project )
            {
                /** {@inheritDoc} */
                public Object getValue( final String expression )
                {
                    try
                    {
                        return ReflectionValueExtractor.evaluate( expression, project );
                    }
                    catch ( Exception e )
                    {
                        addFeedback( "Failed to extract \'" + expression + "\' from: " + project, e );
                    }

                    return null;
                }
            } );

            final DateBean bean = new DateBean();
            interpolator.addValueSource( new ObjectBasedValueSource( bean ) );

            try ( Reader reader = ReaderFactory.newXmlReader( docDescriptor ) )
            {
                final String interpolatedDoc = interpolator.interpolate( IOUtil.toString( reader ) );

                if ( log != null && log.isDebugEnabled() )
                {
                    log.debug( "Interpolated document descriptor ("
                                   + docDescriptor.getAbsolutePath() + ")\n" + interpolatedDoc );
                }

                // No Strict
                return new DocumentXpp3Reader().read( new StringReader( interpolatedDoc ), false );
            }
        }
        catch ( InterpolationException e )
        {
            final IOException io = new IOException( "Error interpolating document descriptor" );
            io.initCause( e );
            throw io;
        }
    }
}
