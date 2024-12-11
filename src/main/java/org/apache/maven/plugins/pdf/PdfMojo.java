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
package org.apache.maven.plugins.pdf;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.input.XmlStreamReader;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.doxia.Doxia;
import org.apache.maven.doxia.docrenderer.AbstractDocumentRenderer;
import org.apache.maven.doxia.docrenderer.DocumentRenderer;
import org.apache.maven.doxia.docrenderer.DocumentRendererContext;
import org.apache.maven.doxia.docrenderer.DocumentRendererException;
import org.apache.maven.doxia.docrenderer.pdf.PdfRenderer;
import org.apache.maven.doxia.document.DocumentMeta;
import org.apache.maven.doxia.document.DocumentModel;
import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.apache.maven.doxia.document.io.xpp3.DocumentXpp3Writer;
import org.apache.maven.doxia.index.IndexEntry;
import org.apache.maven.doxia.index.IndexingSink;
import org.apache.maven.doxia.module.xdoc.XdocSink;
import org.apache.maven.doxia.parser.ParseException;
import org.apache.maven.doxia.parser.manager.ParserNotFoundException;
import org.apache.maven.doxia.sink.impl.SinkAdapter;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Reader;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.doxia.siterenderer.RendererException;
import org.apache.maven.doxia.siterenderer.SiteRenderingContext;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.doxia.tools.SiteToolException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.reporting.exec.MavenReportExecution;
import org.apache.maven.reporting.exec.MavenReportExecutor;
import org.apache.maven.reporting.exec.MavenReportExecutorRequest;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.i18n.I18N;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Generates a PDF document for a project documentation usually published as web site (with maven-site-plugin).
 *
 * @author ltheussl
 */
@Mojo(name = "pdf", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class PdfMojo extends AbstractPdfMojo implements Contextualizable {

    /**
     * The vm line separator
     */
    private static final String EOL = System.getProperty("line.separator");
    /**
     * A comma separated list of locales supported by Maven.
     * The first valid token will be the default Locale for this instance of the Java Virtual Machine.
     */
    @Parameter(property = "locales")
    private String locales;

    /**
     * The Maven Project Object.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * The Maven Settings.
     *
     * @since 1.1
     */
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    /**
     * The current build session instance.
     *
     * @since 1.1
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Directory containing source for apt, fml and xdoc docs.
     */
    @Parameter(defaultValue = "${basedir}/src/site", required = true)
    private File siteDirectory;

    /**
     * Directory containing generated sources for apt, fml and xdoc docs.
     *
     * @since 1.1
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-site", required = true)
    private File generatedSiteDirectory;

    /**
     * Output directory where PDF files should be created.
     */
    @Parameter(defaultValue = "${project.build.directory}/pdf", required = true)
    private File outputDirectory;

    /**
     * Working directory for working files like temp files/resources.
     */
    @Parameter(defaultValue = "${project.build.directory}/pdf", required = true)
    private File workingDirectory;

    /**
     * File that contains the DocumentModel of the PDF to generate.
     */
    @Parameter(defaultValue = "src/site/pdf.xml")
    private File docDescriptor;

    /**
     * Identifies the framework to use for pdf generation: either "fo" (default) or "itext".
     */
    @Parameter(property = "implementation", defaultValue = "fo", required = true)
    private String implementation;

    /**
     * The local repository.
     */
    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    private ArtifactRepository localRepository;

    /**
     * The remote repositories where artifacts are located.
     *
     * @since 1.1
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
    private List<ArtifactRepository> remoteRepositories;

    /**
     * If <code>true</false>, aggregate all source documents in one pdf, otherwise generate one pdf for each
     * source document.
     */
    @Parameter(property = "aggregate", defaultValue = "true")
    private boolean aggregate;

    /**
     * The current version of this plugin.
     */
    @Parameter(defaultValue = "${plugin.version}", readonly = true)
    private String pluginVersion;

    /**
     * If <code>true</code>, generate all Maven reports defined in <code>${project.reporting}</code> and append
     * them as a new entry in the TOC (Table Of Contents).
     * <b>Note</b>: Including the report generation could fail the PDF generation or increase the build time.
     *
     * @since 1.1
     */
    @Parameter(property = "includeReports", defaultValue = "true")
    private boolean includeReports;

    /**
     * Generate a TOC (Table Of Content) for all items defined in the &lt;toc/&gt; element from the document descriptor.
     * <br>
     * Possible values are: 'none', 'start' and 'end'.
     *
     * @since 1.1
     */
    @Parameter(property = "generateTOC", defaultValue = "start")
    private String generateTOC;

    /**
     * Whether to validate xml input documents.
     * If set to true, <strong>all</strong> input documents in xml format
     * (in particular xdoc and fml) will be validated and any error will
     * lead to a build failure.
     *
     * @since 1.2
     */
    @Parameter(property = "validate", defaultValue = "false")
    private boolean validate;

    /**
     * Reports (Maven 2).
     *
     * @since 1.3
     */
    @Parameter(defaultValue = "${reports}", required = true, readonly = true)
    private MavenReport[] reports;

    /**
     * Reports (Maven 3).
     *
     * @since 1.5
     */
    @Parameter(defaultValue = "${project.reporting}", readonly = true)
    private Reporting reporting;

    /**
     * The current document Renderer.
     * @see #implementation
     */
    private DocumentRenderer docRenderer;

    /**
     * The default locale.
     */
    private Locale defaultLocale;

    /**
     * The available locales list.
     */
    private List<Locale> localesList;

    /**
     * The default decoration model.
     */
    private DecorationModel defaultDecorationModel;

    /**
     * The temp Generated Site dir to have generated reports by this plugin.
     *
     * @since 1.1
     */
    private File generatedSiteDirectoryTmp;

    /**
     * A map of generated MavenReport list using locale as key.
     *
     * @since 1.1
     */
    private Map<Locale, List<MavenReport>> generatedMavenReports;

    /**
     * @since 1.3
     */
    private PlexusContainer container;

    /**
     * FO Document Renderer.
     */
    private final PdfRenderer foRenderer;

    /**
     * Internationalization.
     */
    private final I18N i18n;

    /**
     * IText Document Renderer.
     */
    private final PdfRenderer itextRenderer;

    /**
     * Site renderer.
     */
    private final Renderer siteRenderer;

    /**
     * SiteTool.
     */
    private final SiteTool siteTool;

    /**
     * Doxia.
     *
     * @since 1.1
     */
    private final Doxia doxia;

    @Inject
    public PdfMojo(
            @Named("fo") PdfRenderer foRenderer,
            I18N i18n,
            @Named("itext") PdfRenderer itextRenderer,
            Renderer siteRenderer,
            SiteTool siteTool,
            Doxia doxia) {
        this.foRenderer = foRenderer;
        this.i18n = i18n;
        this.itextRenderer = itextRenderer;
        this.siteRenderer = siteRenderer;
        this.siteTool = siteTool;
        this.doxia = doxia;
    }

    /** {@inheritDoc} */
    public void execute() throws MojoExecutionException {
        init();

        try {
            generatePdf();
        } catch (IOException e) {
            debugLogGeneratedModel(getDocumentModel(Locale.ENGLISH));

            throw new MojoExecutionException("Error during document generation: " + e.getMessage(), e);
        }

        try {
            copyGeneratedPdf();
        } catch (IOException e) {
            throw new MojoExecutionException("Error copying generated PDF: " + e.getMessage(), e);
        }
    }

    /** {@inheritDoc} */
    public void contextualize(Context context) throws ContextException {
        container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }

    protected File getOutputDirectory() {
        return outputDirectory;
    }

    protected File getWorkingDirectory() {
        return workingDirectory;
    }

    protected boolean isIncludeReports() {
        return includeReports;
    }

    /**
     * Init and validate parameters
     */
    private void init() {
        if ("fo".equalsIgnoreCase(implementation)) {
            this.docRenderer = foRenderer;
        } else if ("itext".equalsIgnoreCase(implementation)) {
            this.docRenderer = itextRenderer;
        } else {
            getLog().warn("Invalid 'implementation' parameter: '" + implementation + "', using 'fo' as default.");

            this.docRenderer = foRenderer;
        }

        if (!("none".equalsIgnoreCase(generateTOC)
                || "start".equalsIgnoreCase(generateTOC)
                || "end".equalsIgnoreCase(generateTOC))) {
            getLog().warn("Invalid 'generateTOC' parameter: '" + generateTOC + "', using 'start' as default.");

            this.generateTOC = "start";
        }
    }

    /**
     * Copy the generated PDF to outputDirectory.
     *
     * @throws MojoExecutionException if any
     * @throws IOException if any
     * @since 1.1
     */
    private void copyGeneratedPdf() throws MojoExecutionException, IOException {
        boolean requireCopy = !getOutputDirectory()
                .getCanonicalPath()
                .equals(getWorkingDirectory().getCanonicalPath());

        String outputName = getDocumentModel(getDefaultLocale()).getOutputName().trim();
        if (!outputName.endsWith(".pdf")) {
            outputName = outputName.concat(".pdf");
        }

        for (final Locale locale : getAvailableLocales()) {
            File generatedPdfSource = new File(getLocaleDirectory(getWorkingDirectory(), locale), outputName);

            if (!generatedPdfSource.exists()) {
                getLog().warn("Unable to find the generated pdf: " + generatedPdfSource.getAbsolutePath());
                continue;
            }

            File generatedPdfDest = new File(getLocaleDirectory(getOutputDirectory(), locale), outputName);

            if (requireCopy) {
                FileUtils.copyFile(generatedPdfSource, generatedPdfDest);
                generatedPdfSource.delete();
            }

            getLog().info("pdf generated: " + generatedPdfDest);
        }
    }

    /**
     * Generate the PDF.
     *
     * @throws MojoExecutionException if any
     * @throws IOException if any
     * @since 1.1
     */
    private void generatePdf() throws MojoExecutionException, IOException {
        Locale.setDefault(getDefaultLocale());

        for (final Locale locale : getAvailableLocales()) {
            final File workingDir = getLocaleDirectory(getWorkingDirectory(), locale);

            File siteDirectoryFile = getLocaleDirectory(getSiteDirectoryTmp(), locale);

            copyResources(locale);

            // generated xdoc sources for reports
            generateMavenReports(locale);

            // render all Doxia source files to pdf (were handwritten or generated by reports)
            DocumentRendererContext context = new DocumentRendererContext();
            context.put("project", project);
            context.put("settings", settings);
            context.put("PathTool", new PathTool());
            context.put("FileUtils", new FileUtils());
            context.put("StringUtils", new StringUtils());
            context.put("i18n", i18n);
            context.put("generateTOC", generateTOC);
            context.put("validate", validate);

            // Put any of the properties in directly into the Velocity context
            for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
                context.put((String) entry.getKey(), entry.getValue());
            }

            final DocumentModel model = aggregate ? getDocumentModel(locale) : null;

            try {
                // TODO use interface see DOXIASITETOOLS-30
                ((AbstractDocumentRenderer) docRenderer).render(siteDirectoryFile, workingDir, model, context);
            } catch (DocumentRendererException e) {
                throw new MojoExecutionException("Error during document generation: " + e.getMessage(), e);
            }
        }
    }

    /**
     * @return the default tmpGeneratedSiteDirectory when report will be created.
     * @since 1.1
     */
    private File getGeneratedSiteDirectoryTmp() {
        if (this.generatedSiteDirectoryTmp == null) {
            this.generatedSiteDirectoryTmp = new File(getWorkingDirectory(), "generated-site.tmp");
        }

        return this.generatedSiteDirectoryTmp;
    }

    /**
     * Copy all site and generated-site files in the tmpSiteDirectory.
     * <br>
     * <b>Note</b>: ignore copying of <code>generated-site</code> files if they already exist in the
     * <code>site</code> dir.
     *
     * @param tmpSiteDir not null
     * @throws IOException if any
     * @since 1.1
     */
    protected void prepareTempSiteDirectory(final File tmpSiteDir) throws IOException {
        // safety
        tmpSiteDir.mkdirs();

        // copy site
        if (siteDirectory.exists()) {
            FileUtils.copyDirectoryStructure(siteDirectory, tmpSiteDir);
        }

        // Remove SCM files
        List<String> files = FileUtils.getFileAndDirectoryNames(
                tmpSiteDir, FileUtils.getDefaultExcludesAsString(), null, true, true, true, true);
        for (final String fileName : files) {
            final File file = new File(fileName);

            if (file.isDirectory()) {
                FileUtils.deleteDirectory(file);
            } else {
                file.delete();
            }
        }

        copySiteDir(generatedSiteDirectory, tmpSiteDir);
    }

    /**
     * Copy the from site dir to the to dir.
     *
     * @param from not null
     * @param to not null
     * @throws IOException if any
     * @since 1.1
     */
    private void copySiteDir(final File from, final File to) throws IOException {
        if (from == null || !from.exists()) {
            return;
        }

        // copy generated-site
        for (final Locale locale : getAvailableLocales()) {
            String excludes = getDefaultExcludesWithLocales(getAvailableLocales(), getDefaultLocale());
            List<String> siteFiles = siteDirectory.exists()
                    ? FileUtils.getFileNames(siteDirectory, "**/*", excludes, false)
                    : new ArrayList<>();
            File siteDirectoryLocale = new File(siteDirectory, locale.getLanguage());
            if (!locale.getLanguage().equals(getDefaultLocale().getLanguage()) && siteDirectoryLocale.exists()) {
                siteFiles = FileUtils.getFileNames(siteDirectoryLocale, "**/*", excludes, false);
            }

            List<String> generatedSiteFiles = FileUtils.getFileNames(from, "**/*", excludes, false);
            File fromLocale = new File(from, locale.getLanguage());
            if (!locale.getLanguage().equals(getDefaultLocale().getLanguage()) && fromLocale.exists()) {
                generatedSiteFiles = FileUtils.getFileNames(fromLocale, "**/*", excludes, false);
            }

            for (final String generatedSiteFile : generatedSiteFiles) {
                if (siteFiles.contains(generatedSiteFile)) {
                    getLog().warn("Generated-site already contains a file in site: " + generatedSiteFile
                            + ". Ignoring copying it!");
                    continue;
                }

                if (!locale.getLanguage().equals(getDefaultLocale().getLanguage())) {
                    if (fromLocale.exists()) {
                        File in = new File(fromLocale, generatedSiteFile);
                        File out = new File(new File(to, locale.getLanguage()), generatedSiteFile);
                        out.getParentFile().mkdirs();
                        FileUtils.copyFile(in, out);
                    }
                } else {
                    File in = new File(from, generatedSiteFile);
                    File out = new File(to, generatedSiteFile);
                    out.getParentFile().mkdirs();
                    FileUtils.copyFile(in, out);
                }
            }
        }
    }

    /**
     * Constructs a DocumentModel for the current project. The model is either read from
     * a descriptor file, if it exists, or constructed from information in the pom and site.xml.
     *
     * @param locale not null
     * @return DocumentModel.
     * @throws MojoExecutionException if any
     * @see #appendGeneratedReports(DocumentModel, Locale)
     */
    private DocumentModel getDocumentModel(Locale locale) throws MojoExecutionException {
        if (docDescriptor.exists()) {
            DocumentModel doc = getDocumentModelFromDescriptor(locale);
            // TODO: descriptor model should get merged into default model, see MODELLO-63

            appendGeneratedReports(doc, locale);

            saveTOC(doc.getToc(), locale);

            return doc;
        }

        DocumentModel model = new DocumentModelBuilder(project, getDefaultDecorationModel()).getDocumentModel();

        model.getMeta().setGenerator(getDefaultGenerator());
        model.getMeta().setLanguage(locale.getLanguage());
        model.getCover().setCoverType(i18n.getString("pdf-plugin", getDefaultLocale(), "toc.type"));
        model.getToc().setName(i18n.getString("pdf-plugin", getDefaultLocale(), "toc.title"));

        appendGeneratedReports(model, locale);

        saveTOC(model.getToc(), locale);

        debugLogGeneratedModel(model);

        return model;
    }

    /**
     * Read a DocumentModel from a file.
     *
     * @param locale used to set the language.
     * @return the DocumentModel read from the configured document descriptor.
     * @throws org.apache.maven.plugin.MojoExecutionException if the model could not be read.
     */
    private DocumentModel getDocumentModelFromDescriptor(Locale locale) throws MojoExecutionException {
        DocumentModel model;

        try {
            model = new DocumentDescriptorReader(project, getLog(), locale)
                    .readAndFilterDocumentDescriptor(docDescriptor);
        } catch (XmlPullParserException ex) {
            throw new MojoExecutionException("Error reading DocumentDescriptor!", ex);
        } catch (IOException io) {
            throw new MojoExecutionException("Error opening DocumentDescriptor!", io);
        }

        if (model.getMeta() == null) {
            model.setMeta(new DocumentMeta());
        }

        if (StringUtils.isEmpty(model.getMeta().getLanguage())) {
            model.getMeta().setLanguage(locale.getLanguage());
        }

        if (StringUtils.isEmpty(model.getMeta().getGenerator())) {
            model.getMeta().setGenerator(getDefaultGenerator());
        }

        return model;
    }

    /**
     * Return the directory for a given Locale and the current default Locale.
     *
     * @param basedir the base directory
     * @param locale a Locale.
     * @return File.
     */
    private File getLocaleDirectory(File basedir, Locale locale) {
        if (locale.getLanguage().equals(getDefaultLocale().getLanguage())) {
            return basedir;
        }

        return new File(basedir, locale.getLanguage());
    }

    /**
     * @return the default locale from <code>siteTool</code>.
     * @see #getAvailableLocales()
     */
    private Locale getDefaultLocale() {
        if (this.defaultLocale == null) {
            this.defaultLocale = getAvailableLocales().get(0);
        }

        return this.defaultLocale;
    }

    /**
     * @return the available locales from <code>siteTool</code>.
     */
    private List<Locale> getAvailableLocales() {
        if (this.localesList == null) {
            this.localesList = siteTool.getSiteLocales(locales);
        }

        return this.localesList;
    }

    /**
     * @return the DecorationModel instance from <code>site.xml</code>
     * @throws MojoExecutionException if any
     */
    private DecorationModel getDefaultDecorationModel() throws MojoExecutionException {
        if (this.defaultDecorationModel == null) {
            final Locale locale = getDefaultLocale();

            final File descriptorFile = siteTool.getSiteDescriptor(siteDirectory, locale);
            DecorationModel decoration = null;

            if (descriptorFile.exists()) {
                try (XmlStreamReader reader = new XmlStreamReader(descriptorFile)) {
                    String siteDescriptorContent = IOUtil.toString(reader);

                    siteDescriptorContent = siteTool.getInterpolatedSiteDescriptorContent(
                            new HashMap<>(2), project, siteDescriptorContent);

                    decoration = new DecorationXpp3Reader().read(new StringReader(siteDescriptorContent));
                } catch (XmlPullParserException e) {
                    throw new MojoExecutionException("Error parsing site descriptor", e);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error reading site descriptor", e);
                } catch (SiteToolException e) {
                    throw new MojoExecutionException("Error when interpoling site descriptor", e);
                }
            }

            this.defaultDecorationModel = decoration;
        }

        return this.defaultDecorationModel;
    }

    /**
     * Parse the decoration model to find the skin artifact and copy its resources to the output dir.
     *
     * @param locale not null
     * @throws MojoExecutionException if any
     * @see #getDefaultDecorationModel()
     */
    private void copyResources(Locale locale) throws MojoExecutionException {
        final DecorationModel decorationModel = getDefaultDecorationModel();
        if (decorationModel == null) {
            return;
        }

        Artifact skinArtifact;
        try {
            skinArtifact = siteTool.getSkinArtifactFromRepository(
                    localRepository, project.getRemoteArtifactRepositories(), decorationModel);
        } catch (SiteToolException e) {
            throw new MojoExecutionException("SiteToolException: " + e.getMessage(), e);
        }

        if (skinArtifact == null) {
            return;
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Copy resources from skin artifact: '" + skinArtifact.getId() + "'...");
        }

        try {
            final SiteRenderingContext context = siteRenderer.createContextForSkin(
                    skinArtifact, new HashMap<>(2), decorationModel, project.getName(), locale);
            context.addSiteDirectory(new File(siteDirectory, locale.getLanguage()));

            siteRenderer.copyResources(context, getWorkingDirectory());
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        } catch (RendererException e) {
            throw new MojoExecutionException("RendererException: " + e.getMessage(), e);
        }
    }

    /**
     * Construct a default producer.
     *
     * @return A String in the form <code>Maven PDF Plugin v. 1.1.1, 'fo' implementation</code>.
     */
    private String getDefaultGenerator() {
        return "Maven PDF Plugin v. " + pluginVersion + ", '" + implementation + "' implementation.";
    }

    /**
     * Write the auto-generated model to disc.
     *
     * @param docModel the model to write.
     */
    private void debugLogGeneratedModel(final DocumentModel docModel) {
        if (getLog().isDebugEnabled() && project != null) {
            final File outputDir = new File(project.getBuild().getDirectory(), "pdf");

            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            final File doc = FileUtils.createTempFile("pdf", ".xml", outputDir);
            final DocumentXpp3Writer xpp3 = new DocumentXpp3Writer();

            try (Writer writer = WriterFactory.newXmlWriter(doc)) {
                xpp3.write(writer, docModel);
                getLog().debug("Generated a default document model: " + doc.getAbsolutePath());
            } catch (IOException e) {
                getLog().error("Failed to write document model: " + e.getMessage());
                getLog().debug(e);
            }
        }
    }

    /**
     * Generate all Maven reports defined in <code>${project.reporting}</code> to <code>xdoc</code> source
     * only if <code>generateReports</code> is enabled.
     *
     * @param locale not null
     * @throws MojoExecutionException if any
     * @throws IOException if any
     * @since 1.1
     */
    private void generateMavenReports(Locale locale) throws MojoExecutionException, IOException {
        if (!isIncludeReports()) {
            getLog().info("Skipped report generation.");
            return;
        }

        if (project.getReporting() == null) {
            getLog().info("No report was specified.");
            return;
        }

        List<MavenReportExecution> reportExecutions = getReports();
        for (MavenReportExecution reportExecution : reportExecutions) {
            generateMavenReport(reportExecution, locale);
        }

        // copy generated site
        copySiteDir(getGeneratedSiteDirectoryTmp(), getSiteDirectoryTmp());
        copySiteDir(generatedSiteDirectory, getSiteDirectoryTmp());
    }

    /**
     * Generate the given Maven report to an xdoc source file,
     * only if it is not an external report and the report could be generated.
     *
     * @param reportExecution not null
     * @param locale not null
     * @throws IOException if any
     * @throws MojoExecutionException if any
     * @since 1.1
     */
    private void generateMavenReport(MavenReportExecution reportExecution, Locale locale)
            throws IOException, MojoExecutionException {
        MavenReport report = reportExecution.getMavenReport();

        String localReportName = report.getName(locale);

        if (!reportExecution.canGenerateReport()) {
            getLog().info("Skipped \"" + localReportName + "\" report.");
            getLog().debug("canGenerateReport() was false.");

            return;
        }

        if (report.isExternalReport()) {
            getLog().info("Skipped external \"" + localReportName + "\" report (not supported by pdf plugin).");
            getLog().debug("isExternalReport() was false.");

            return;
        }

        for (final MavenReport generatedReport : getGeneratedMavenReports(locale)) {
            if (report.getName(locale).equals(generatedReport.getName(locale))) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(report.getName(locale) + " was already generated.");
                }
                return;
            }
        }

        File outDir = new File(getGeneratedSiteDirectoryTmp(), "xdoc");
        if (!locale.getLanguage().equals(defaultLocale.getLanguage())) {
            outDir = new File(new File(getGeneratedSiteDirectoryTmp(), locale.getLanguage()), "xdoc");
        }
        outDir.mkdirs();

        File generatedReport = new File(outDir, report.getOutputName() + ".xml");

        if (siteDirectory.exists()) {
            String excludes = getDefaultExcludesWithLocales(getAvailableLocales(), getDefaultLocale());
            List<String> files =
                    FileUtils.getFileNames(siteDirectory, "*/" + report.getOutputName() + ".*", excludes, false);
            if (!locale.getLanguage().equals(defaultLocale.getLanguage())) {
                files = FileUtils.getFileNames(
                        new File(siteDirectory, locale.getLanguage()),
                        "*/" + report.getOutputName() + ".*",
                        excludes,
                        false);
            }

            if (files.size() != 0) {
                String displayLanguage = locale.getDisplayLanguage(Locale.ENGLISH);

                if (getLog().isInfoEnabled()) {
                    getLog().info("Skipped \"" + report.getName(locale) + "\" report, file \""
                            + report.getOutputName() + "\" already exists for the " + displayLanguage
                            + " version.");
                }

                return;
            }
        }

        if (getLog().isInfoEnabled()) {
            getLog().info("Generating \"" + localReportName + "\" report.");
        }

        // The report will eventually generate output by itself, so we set its output directory anyway.
        report.setReportOutputDirectory(outDir);

        StringWriter sw = new StringWriter();

        PdfXdocSink pdfXdocSink = null;
        try {
            pdfXdocSink = new PdfXdocSink(sw);
            renderReportToSink(reportExecution, locale, pdfXdocSink);
        } catch (MavenReportException e) {
            String goal = reportExecution.getPlugin().getArtifactId()
                    + ':'
                    + reportExecution.getPlugin().getVersion()
                    + ':'
                    + reportExecution.getGoal();
            throw new MojoExecutionException("Error generating " + goal + " report", e);
        } finally {
            if (pdfXdocSink != null) {
                pdfXdocSink.close();
            }
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Writing generated xdoc to " + generatedReport);
        }
        writeGeneratedReport(sw.toString(), generatedReport);

        // keep generated report xdoc only if it is valid
        if (isValidGeneratedReportXdoc(
                reportExecution.getPlugin().getId() + ':' + reportExecution.getGoal(),
                generatedReport,
                localReportName)) {
            getGeneratedMavenReports(locale).add(report);
        }
    }

    /**
     * see org.apache.maven.plugins.site.render.ReportDocumentRenderer#renderDocument(...)
     *
     * @param reportExec
     * @param locale
     * @param sink
     * @throws MavenReportException
     */
    private void renderReportToSink(MavenReportExecution reportExec, Locale locale, PdfXdocSink sink)
            throws MavenReportException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            if (reportExec.getClassLoader() != null) {
                Thread.currentThread().setContextClassLoader(reportExec.getClassLoader());
            }

            MavenReport report = reportExec.getMavenReport();

            /*if ( report instanceof MavenMultiPageReport )
            {
                // extended multi-page API
                ( (MavenMultiPageReport) report ).generate( mainSink, multiPageSinkFactory, locale );
            }
            else if ( generateMultiPage( locale, multiPageSinkFactory, mainSink ) )
            {
                // extended multi-page API for Maven 2.2, only accessible by reflection API
            }
            else
            {*/
            // old single-page-only API
            report.generate(sink, locale);
            // }
        } finally {
            if (reportExec.getClassLoader() != null) {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    /**
     * @param locale not null
     * @return the generated reports
     * @since 1.1
     */
    private List<MavenReport> getGeneratedMavenReports(Locale locale) {
        if (this.generatedMavenReports == null) {
            this.generatedMavenReports = new HashMap<>(2);
        }

        this.generatedMavenReports.computeIfAbsent(locale, k -> new ArrayList<>(2));

        return this.generatedMavenReports.get(locale);
    }

    /**
     * Append generated reports to the toc only if <code>generateReports</code> is enabled, for instance:
     * <pre>
     * &lt;item name="Project Reports" ref="project-info"&gt;
     * &nbsp;&nbsp;&lt;item name="Project License" ref="license" /&gt;
     * &nbsp;&nbsp;&lt;item name="Project Team" ref="team-list" /&gt;
     * &nbsp;&nbsp;&lt;item name="Continuous Integration" ref="integration" /&gt;
     * &nbsp;&nbsp;...
     * &lt;/item&gt;
     * </pre>
     *
     * @param model not null
     * @param locale not null
     * @see #generateMavenReports(Locale)
     * @since 1.1
     */
    protected void appendGeneratedReports(DocumentModel model, Locale locale) {
        if (!isIncludeReports()) {
            return;
        }
        if (getGeneratedMavenReports(locale).isEmpty()) {
            return;
        }

        final DocumentTOCItem documentTOCItem = new DocumentTOCItem();
        documentTOCItem.setName(i18n.getString("pdf-plugin", locale, "toc.project-info.item"));
        documentTOCItem.setRef("project-info"); // see #generateMavenReports(Locale)

        List<String> addedRef = new ArrayList<>(4);

        List<DocumentTOCItem> items = new ArrayList<>(4);

        // append generated report defined as MavenReport
        for (final MavenReport report : getGeneratedMavenReports(locale)) {
            final DocumentTOCItem reportItem = new DocumentTOCItem();
            reportItem.setName(report.getName(locale));
            reportItem.setRef(report.getOutputName());

            items.add(reportItem);

            addedRef.add(report.getOutputName());
        }

        // append all generated reports from generated-site
        try {
            if (generatedSiteDirectory.exists()) {
                String excludes = getDefaultExcludesWithLocales(getAvailableLocales(), getDefaultLocale());
                List<String> generatedDirs = FileUtils.getDirectoryNames(generatedSiteDirectory, "*", excludes, true);
                if (!locale.getLanguage().equals(getDefaultLocale().getLanguage())) {
                    generatedDirs = FileUtils.getFileNames(
                            new File(generatedSiteDirectory, locale.getLanguage()), "*", excludes, true);
                }

                for (final String generatedDir : generatedDirs) {
                    List<String> generatedFiles =
                            FileUtils.getFileNames(new File(generatedDir), "**.*", excludes, false);

                    for (final String generatedFile : generatedFiles) {
                        final String ref = generatedFile.substring(0, generatedFile.lastIndexOf('.'));

                        if (!addedRef.contains(ref)) {
                            final String title = getGeneratedDocumentTitle(new File(generatedDir, generatedFile));

                            if (title != null) {
                                final DocumentTOCItem reportItem = new DocumentTOCItem();
                                reportItem.setName(title);
                                reportItem.setRef(ref);

                                items.add(reportItem);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            getLog().error("IOException: " + e.getMessage());
            getLog().debug(e);
        }

        // append to Toc
        documentTOCItem.setItems(items);
        model.getToc().addItem(documentTOCItem);
    }

    private void saveTOC(DocumentTOC toc, Locale locale) {
        try {
            TocFileHelper.saveTOC(getWorkingDirectory(), toc, locale);
        } catch (IOException e) {
            getLog().error("Error while writing table of contents", e);
        }
    }

    /**
     * Parse a generated Doxia file and returns its title.
     *
     * @param f not null
     * @return the xdoc file title or null if an error occurs.
     * @throws IOException if any
     * @since 1.1
     */
    private String getGeneratedDocumentTitle(final File f) throws IOException {
        final IndexEntry entry = new IndexEntry("index");
        final IndexingSink titleSink = new IndexingSink(entry);

        try (Reader reader = ReaderFactory.newXmlReader(f)) {
            doxia.parse(reader, f.getParentFile().getName(), titleSink);
        } catch (ParseException e) {
            getLog().error("ParseException: " + e.getMessage());
            getLog().debug(e);
            return null;
        } catch (ParserNotFoundException e) {
            getLog().error("ParserNotFoundException: " + e.getMessage());
            getLog().debug(e);
            return null;
        }

        return titleSink.getTitle();
    }

    /**
     * Parsing the generated report to see if it is correct or not. Log the error for the user.
     *
     * @param fullGoal not null
     * @param generatedReport not null
     * @param localReportName not null
     * @return <code>true</code> if Doxia is able to parse the generated report, <code>false</code> otherwise.
     * @since 1.1
     */
    private boolean isValidGeneratedReportXdoc(String fullGoal, File generatedReport, String localReportName) {
        SinkAdapter sinkAdapter = new SinkAdapter();
        try (Reader reader = ReaderFactory.newXmlReader(generatedReport)) {
            doxia.parse(reader, "xdoc", sinkAdapter);
        } catch (ParseException e) {
            String sb = EOL
                    + "Error when parsing the generated report xdoc file: "
                    + generatedReport.getAbsolutePath() + EOL
                    + e.getMessage() + EOL
                    + "You could:" + EOL
                    + "  * exclude all reports using -DincludeReports=false" + EOL
                    + "  * remove the "
                    + fullGoal
                    + " from the <reporting/> part. To not affect the site generation, "
                    + "you could create a PDF profile." + EOL
                    + "Ignoring the \"" + localReportName + "\" report in the PDF." + EOL;
            getLog().error(sb);
            getLog().debug(e);

            return false;
        } catch (ParserNotFoundException e) {
            getLog().error("ParserNotFoundException: " + e.getMessage());
            getLog().debug(e);

            return false;
        } catch (IOException e) {
            getLog().error("IOException: " + e.getMessage());
            getLog().debug(e);

            return false;
        }

        return true;
    }

    protected List<MavenReportExecution> getReports() throws MojoExecutionException {
        MavenReportExecutorRequest mavenReportExecutorRequest = new MavenReportExecutorRequest();
        mavenReportExecutorRequest.setLocalRepository(localRepository);
        mavenReportExecutorRequest.setMavenSession(session);
        mavenReportExecutorRequest.setProject(project);
        mavenReportExecutorRequest.setReportPlugins(getReportingPlugins());

        MavenReportExecutor mavenReportExecutor;
        try {
            mavenReportExecutor = (MavenReportExecutor) container.lookup(MavenReportExecutor.class.getName());
        } catch (ComponentLookupException e) {
            throw new MojoExecutionException("could not get MavenReportExecutor component", e);
        }
        return mavenReportExecutor.buildMavenReports(mavenReportExecutorRequest);
    }

    /**
     * Get the report plugins from reporting section, adding if necessary (i.e. not excluded)
     * default reports (i.e. maven-project-info-reports)
     *
     * @return the effective list of reports
     * @since 1.5
     */
    private ReportPlugin[] getReportingPlugins() {
        List<ReportPlugin> reportingPlugins = reporting.getPlugins();

        // MSITE-806: add default report plugin like done in maven-model-builder DefaultReportingConverter
        boolean hasMavenProjectInfoReportsPlugin = false;
        for (ReportPlugin plugin : reportingPlugins) {
            if ("org.apache.maven.plugins".equals(plugin.getGroupId())
                    && "maven-project-info-reports-plugin".equals(plugin.getArtifactId())) {
                hasMavenProjectInfoReportsPlugin = true;
                break;
            }
        }

        if (!reporting.isExcludeDefaults() && !hasMavenProjectInfoReportsPlugin) {
            ReportPlugin mpir = new ReportPlugin();
            mpir.setArtifactId("maven-project-info-reports-plugin");
            reportingPlugins.add(mpir);
        }
        return reportingPlugins.toArray(new ReportPlugin[0]);
    }

    /**
     * Write the given content to the given file.
     * <br>
     * <b>Note</b>: try also to fix the content due to some issues in
     * {@link org.apache.maven.reporting.AbstractMavenReport}.
     *
     * @param content the given content
     * @param toFile the report file
     * @throws IOException if any
     * @since 1.1
     */
    private static void writeGeneratedReport(String content, File toFile) throws IOException {
        if (content == null || content.isEmpty()) {
            return;
        }

        try (Writer writer = WriterFactory.newXmlWriter(toFile)) {
            // see PdfSink#table()
            writer.write(StringUtils.replace(content, "<table><table", "<table"));
        }
    }

    /**
     * @param locales the list of locales dir to exclude
     * @param defaultLocale the default locale.
     * @return the comma separated list of default excludes and locales dir.
     * @see FileUtils#getDefaultExcludesAsString()
     * @since 1.1
     */
    private static String getDefaultExcludesWithLocales(List<Locale> locales, Locale defaultLocale) {
        StringBuilder excludesLocales = new StringBuilder(FileUtils.getDefaultExcludesAsString());
        for (final Locale locale : locales) {
            if (!locale.getLanguage().equals(defaultLocale.getLanguage())) {
                excludesLocales.append(",**/").append(locale.getLanguage()).append("/*");
            }
        }

        return excludesLocales.toString();
    }

    /**
     * A sink to render a Maven report as a generated xdoc file, with some known workarounds.
     *
     * @since 1.1
     */
    private static class PdfXdocSink extends XdocSink implements org.codehaus.doxia.sink.Sink {
        protected PdfXdocSink(Writer writer) {
            super(writer);
        }

        /** {@inheritDoc} */
        public void text(String text) {
            // workaround to fix quotes introduced with MPIR-59 (then removed in MPIR-136)
            super.text(StringUtils.replace(text, "\u0092", "'"));
        }

        public void tableRow() {
            // To be backward compatible: TODO add to XdocSink
            if (!this.tableRows) {
                tableRows(null, false);
            }
            super.tableRow(null);
        }
    }
}
