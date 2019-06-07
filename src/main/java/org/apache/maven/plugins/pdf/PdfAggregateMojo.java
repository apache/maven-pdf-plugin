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

import org.apache.maven.doxia.document.DocumentModel;
import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.apache.maven.model.Reporting;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Forks {@code pdf} goal then aggregates PDF content from all modules in the reactor.
 *
 * @author anthony-beurive
 * @since 1.5
 */
@Mojo( name = "aggregate", aggregator = true, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
@Execute( goal = "pdf" )
// TODO should extend AbstractPdfMojo, but requires extensive refactoring
public class PdfAggregateMojo extends PdfMojo
{
    /**
     * The reactor projects.
     */
    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<MavenProject> reactorProjects;

    /**
     * Output directory where aggregated PDF files should be created.
     */
    @Parameter( defaultValue = "${project.build.directory}/pdf-aggregate", required = true )
    private File aggregatedOutputDirectory;

    /**
     * Working directory for aggregated working files like temp files/resources.
     */
    @Parameter( defaultValue = "${project.build.directory}/pdf-aggregate", required = true )
    private File aggregatedWorkingDirectory;

    protected File getOutputDirectory()
    {
        return aggregatedOutputDirectory;
    }

    protected File getWorkingDirectory()
    {
        return aggregatedWorkingDirectory;
    }

    protected boolean isIncludeReports()
    {
        return false; // reports were generate (or not) during pdf:pdf: here, we only aggregate
    }

    protected void prepareTempSiteDirectory( final File tmpSiteDir )
    {
        tmpSiteDir.mkdirs();
    }

    @Override
    protected void appendGeneratedReports( DocumentModel model, Locale locale )
    {
        super.appendGeneratedReports( model, locale );

        getLog().info( "Appending staged reports." );

        DocumentTOC toc = model.getToc();

        File dstSiteTmp = null;
        try
        {
            dstSiteTmp = getSiteDirectoryTmp();
        }
        catch ( IOException ioe )
        {
            getLog().error( "unexpected IOException while getting aggregator root tmp site dir", ioe );
        }
        if ( !dstSiteTmp.exists() )
        {
            getLog().error( "Top-level project does not have src.tmp directory" );
            return;
        }

        for ( MavenProject reactorProject : reactorProjects )
        {
            getLog().info( "Appending " + reactorProject.getArtifactId() + " reports." );

            copySiteDirectoryTmp( reactorProject, dstSiteTmp );

            addTOCItems( toc, reactorProject );
        }
    }

    private void copySiteDirectoryTmp( MavenProject project, File dstSiteTmp )
    {
        Reporting reporting = project.getReporting();
        if ( reporting == null )
        {
            getLog().info( "Skipping reactor project " + project + ": no reporting" );
            return;
        }

        File srcSiteTmp = getModuleSiteDirectoryTmp( project );
        if ( !srcSiteTmp.exists() )
        {
            getLog().info( "Skipping reactor project " + project + ": no site.tmp directory" );
            return;
        }

        String stagedId = getStagedId( project );

        try
        {
            String defaultExcludes = FileUtils.getDefaultExcludesAsString();
            List<String> srcDirNames = FileUtils.getDirectoryNames( srcSiteTmp, "*", defaultExcludes, false );
            for ( String srcDirName : srcDirNames )
            {
                File srcDir = new File( srcSiteTmp, srcDirName );
                File dstDir = new File( new File( dstSiteTmp, srcDirName ), stagedId );
                if ( !dstDir.exists() && !dstDir.mkdirs() )
                {
                    getLog().error( "Could not create directory: " + dstDir );
                    return;
                }

                FileUtils.copyDirectoryStructure( srcDir, dstDir );
            }
        }
        catch ( IOException e )
        {
            getLog().error( "Error while copying sub-project " + project.getArtifactId()
                                    + " site.tmp: " + e.getMessage(), e );
        }
    }

    private void addTOCItems( DocumentTOC topLevelToc, MavenProject project )
    {
        String stagedId = getStagedId( project );

        Map<String, Object> toc = loadToc( project );

        List<Map<String, Object>> items = (ArrayList) toc.get( "items" );

        DocumentTOCItem tocItem = new DocumentTOCItem();
        tocItem.setName( project.getName() );
        tocItem.setRef( stagedId );

        if ( items.size() == 1 && "project-info".equals( items.get( 0 ).get( "ref" ) ) )
        {
            // Special case where a sub-project only contains generated reports.
            items = (List) items.get( 0 ).get( "items" );
        }

        for ( Map<String, Object> item : items )
        {
            addTOCItems( tocItem, item, stagedId );
        }

        topLevelToc.addItem( tocItem );
    }

    private Map<String, Object> loadToc( MavenProject project )
    {
        try
        {
            return TocFileHelper.loadToc( getModuleWorkingDirectory( project ) );
        }
        catch ( IOException e )
        {
            getLog().error( "Error while reading table of contents of module " + project.getArtifactId(), e );
            return Collections.emptyMap();
        }
    }

    private void addTOCItems( DocumentTOCItem parent, Map<String, Object> item, String stagedId )
    {
        DocumentTOCItem tocItem = new DocumentTOCItem();
        tocItem.setName( (String) item.get( "name" ) );
        tocItem.setRef( stagedId + "/" + item.get( "ref" ) );

        List<Map<String, Object>> items = (ArrayList) item.get( "items" );

        for ( Map<String, Object> it : items )
        {
            addTOCItems( tocItem, it, stagedId );
        }

        parent.addItem( tocItem );
    }

    private String getStagedId( MavenProject p )
    {
        Deque<String> projectPath = new ArrayDeque<>();
        projectPath.addFirst( p.getArtifactId() );
        while ( p.getParent() != null )
        {
            p = p.getParent();
            projectPath.addFirst( p.getArtifactId() );
        }

        StringBuilder stagedId = new StringBuilder();
        Iterator<String> artifactIds = projectPath.iterator();
        while ( artifactIds.hasNext() )
        {
            stagedId.append( artifactIds.next() );
            if ( artifactIds.hasNext() )
            {
                stagedId.append( '/' );
            }
        }
        return stagedId.toString();
    }

    private File getModuleWorkingDirectory( MavenProject project )
    {
        return new File( project.getBuild().getDirectory(), "pdf" );
    }

    private File getModuleSiteDirectoryTmp( MavenProject project )
    {
        return new File( getModuleWorkingDirectory( project ), "site.tmp" );
    }
}
