package org.apache.maven.report.projectinfo;

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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Site;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.i18n.I18N;

/**
 * Generates the Project Modules report.
 *
 * @author ltheussl
 * @since 2.2
 */
@Mojo( name = "modules" )
public class ModulesReport
    extends AbstractProjectInfoReport
{

    /**
     * If true the report will include the coordinates (groupId, artifactId, packaging and version) of each module.
     *
     * @since 3.2
     */
    @Parameter( property = "project-info.modules.reportCoordinates", defaultValue = "true" )
    private boolean reportCoordinates = true;

    /**
     * If true the report will include the Maven Central links for each module. Note that this is only respected if
     * reportCoordinates is set to true.
     *
     * @since 3.2
     */
    @Parameter( property = "project-info.modules.reportCoordinates.mavenCentralLinks", defaultValue = "true" )
    private boolean mavenCentralLinks = true;

    /**
     * If true the report will include the parent multi module as well.
     *
     * @since 3.2
     */
    @Parameter( property = "project-info.modules.includeParentMultiModule", defaultValue = "true" )
    private boolean includeParentMultiModule = true;
    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    @Override
    public boolean canGenerateReport()
    {
        boolean result = super.canGenerateReport();
        if ( result && skipEmptyReport )
        {
            result = !isEmpty( getProject().getModel().getModules() );
        }

        return result;
    }

    @Override
    public void executeReport( Locale locale )
    {
        new ModulesRenderer( getSink(), getProject(), getReactorProjects(), projectBuilder, localRepository,
                getI18N( locale ), locale, getLog(),
                reportCoordinates, mavenCentralLinks, includeParentMultiModule,
                siteTool ).render();
    }

    /** {@inheritDoc} */
    public String getOutputName()
    {
        return "modules";
    }

    @Override
    protected String getI18Nsection()
    {
        return "modules";
    }

    // ----------------------------------------------------------------------
    // Private
    // ----------------------------------------------------------------------

    /**
     * Internal renderer class
     */
    static class ModulesRenderer
        extends AbstractProjectInfoRenderer
    {

        private static final String MAVEN_CENTRAL_URL = "http://search.maven.org/";

        protected final Log log;

        protected MavenProject project;

        protected List<MavenProject> reactorProjects;

        protected ProjectBuilder projectBuilder;

        protected ArtifactRepository localRepository;

        protected final boolean reportCoordinates;

        protected final boolean mavenCentralLinks;

        protected final boolean includeParentMultiModule;

        protected SiteTool siteTool;

        ModulesRenderer( Sink sink, MavenProject project, List<MavenProject> reactorProjects,
                         ProjectBuilder projectBuilder, ArtifactRepository localRepository, I18N i18n,
                         Locale locale, Log log,
                         boolean reportCoordinates, boolean mavenCentralLinks, boolean includeParentMultiModule,
                         SiteTool siteTool )
        {
            super( sink, i18n, locale );

            this.project = project;
            this.reactorProjects = reactorProjects;
            this.projectBuilder = projectBuilder;
            this.localRepository = localRepository;
            this.reportCoordinates = reportCoordinates;
            this.mavenCentralLinks = mavenCentralLinks;
            this.includeParentMultiModule = includeParentMultiModule;
            this.siteTool = siteTool;
            this.log = log;
        }

        @Override
        protected String getI18Nsection()
        {
            return "modules";
        }

        @Override
        public void renderBody()
        {
            List<String> modules = project.getModel().getModules();

            if ( modules == null || modules.isEmpty() )
            {
                startSection( getTitle() );

                paragraph( getI18nString( "nolist" ) );

                endSection();

                return;
            }

            startSection( getTitle() );

            paragraph( getI18nString( "intro" ) );

            startTable();

            List<String> tableHeader = getTableHeader();
            tableHeader( tableHeader.toArray( new String[tableHeader.size()] ) );

            final String baseUrl = getDistMgmntSiteUrl( project );

            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
            buildingRequest.setLocalRepository( localRepository );
            
            for ( String module : modules )
            {
                MavenProject moduleProject = getModuleFromReactor( project, reactorProjects, module );

                if ( moduleProject == null )
                {
                    log.warn( "Module " + module + " not found in reactor: loading locally" );

                    File f = new File( project.getBasedir(), module + "/pom.xml" );
                    if ( f.exists() )
                    {
                        try
                        {
                            moduleProject = projectBuilder.build( f, buildingRequest ).getProject();
                        }
                        catch ( ProjectBuildingException e )
                        {
                            throw new IllegalStateException( "Unable to read local module POM", e );
                        }
                    }
                    else
                    {
                        moduleProject = new MavenProject();
                        moduleProject.setName( module );
                        moduleProject.setDistributionManagement( new DistributionManagement() );
                        moduleProject.getDistributionManagement().setSite( new Site() );
                        moduleProject.getDistributionManagement().getSite().setUrl( module );
                    }
                }
                addProjectRow( moduleProject, baseUrl );
            }

            if ( includeParentMultiModule )
            {
                addProjectRow( project, baseUrl );
            }

            endTable();

            endSection();
        }

        private MavenProject getModuleFromReactor( MavenProject project, List<MavenProject> reactorProjects,
                                                   String module )
        {
            // Mainly case of unit test
            if ( reactorProjects == null )
            {
                return null;
            }
            try
            {
                File moduleBasedir = new File( project.getBasedir(), module ).getCanonicalFile();

                for ( MavenProject reactorProject : reactorProjects )
                {
                    if ( moduleBasedir.equals( reactorProject.getBasedir() ) )
                    {
                        return reactorProject;
                    }
                }
            }
            catch ( IOException e )
            {
                log.error( "Error while populating modules menu: " + e.getMessage(), e );
            }
            // module not found in reactor
            return null;
        }

        /**
         * Return distributionManagement.site.url if defined, null otherwise.
         *
         * @param project not null
         * @return could be null
         */
        private static String getDistMgmntSiteUrl( MavenProject project )
        {
            return getDistMgmntSiteUrl( project.getDistributionManagement() );
        }

        private static String getDistMgmntSiteUrl( DistributionManagement distMgmnt )
        {
            if ( distMgmnt != null && distMgmnt.getSite() != null && distMgmnt.getSite().getUrl() != null )
            {
                return urlEncode( distMgmnt.getSite().getUrl() );
            }

            return null;
        }

        private static String urlEncode( final String url )
        {
            if ( url == null )
            {
                return null;
            }

            try
            {
                return new File( url ).toURI().toURL().toExternalForm();
            }
            catch ( MalformedURLException ex )
            {
                return url; // this will then throw somewhere else
            }
        }

        private void addProjectRow( MavenProject moduleProject, final String baseUrl )
        {
            // TODO: find a better filter mechanism, e.g. how to skip the site for those modules completely
            if ( !moduleProject.getArtifactId().contains( "sample" ) )
            {
                List<String> tableRow = getTableRow( moduleProject, baseUrl );
                tableRow( tableRow.toArray( new String[tableRow.size()] ) );
            }
        }

        // adapted from DefaultSiteTool#appendMenuItem
        private String getRelativeLink( String baseUrl, String href, String defaultHref )
        {
            String selectedHref = href;

            if ( selectedHref == null )
            {
                selectedHref = defaultHref;
            }

            if ( baseUrl != null )
            {
                selectedHref = siteTool.getRelativePath( selectedHref, baseUrl );
            }

            if ( selectedHref.endsWith( "/" ) )
            {
                selectedHref = selectedHref.concat( "index.html" );
            }
            else
            {
                selectedHref = selectedHref.concat( "/index.html" );
            }

            return selectedHref;
        }

        private String linkedName( String name, String link )
        {
            return "{" + name + ", ./" + link + "}";
        }

        private List<String> getTableHeader()
        {
            List<String> tableHeader = new ArrayList<>();
            tableHeader.add( getI18nString( "header.name" ) );
            if ( reportCoordinates )
            {
                tableHeader.add( getI18nString( "header.groupId" ) );
                tableHeader.add( getI18nString( "header.artifactId" ) );
                tableHeader.add( getI18nString( "header.version" ) );
                tableHeader.add( getI18nString( "header.packaging" ) );
            }
            tableHeader.add( getI18nString( "header.description" ) );
            return tableHeader;
        }

        private List<String> getTableRow( MavenProject moduleProject, String baseUrl )
        {
            List<String> tableRow = new ArrayList<>();

            final String moduleName =
                    ( moduleProject.getName() == null ) ? moduleProject.getArtifactId() : moduleProject.getName();
            final String moduleHref =
                    getRelativeLink( baseUrl, getDistMgmntSiteUrl( moduleProject ), moduleProject.getArtifactId() );
            tableRow.add( linkedName( moduleName, moduleHref ) );
            if ( reportCoordinates )
            {
                tableRow.add( mavenCentralLinks ? getMavenCentralGroupIdLinkedName( moduleProject.getGroupId() )
                        : moduleProject.getGroupId() );
                tableRow.add( mavenCentralLinks ? getMavenCentralArtifactIdLinkedName( moduleProject.getGroupId(),
                        moduleProject.getArtifactId() ) : moduleProject.getArtifactId() );
                tableRow.add( mavenCentralLinks ? getMavenCentralVersionLinkedName( moduleProject.getGroupId(),
                        moduleProject.getArtifactId(), moduleProject.getVersion() ) : moduleProject.getVersion() );
                tableRow.add( mavenCentralLinks ? getMavenCentralPackagingLinkName( moduleProject.getGroupId(),
                        moduleProject.getArtifactId(), moduleProject.getVersion(),
                        moduleProject.getPackaging() ) : moduleProject.getPackaging() );
            }
            tableRow.add( moduleProject.getDescription() );
            return tableRow;
        }

        private String getMavenCentralGroupIdLinkedName( String groupId )
        {
            return linkedName( groupId, getMavenCentralGroupIdLink( groupId ) );
        }

        private static String getMavenCentralGroupIdLink( String groupId )
        {
            return MAVEN_CENTRAL_URL + "#search|ga|1|g%3A%22" + groupId + "%22";
        }

        private String getMavenCentralArtifactIdLinkedName( String groupId, String artifactId )
        {
            return linkedName( artifactId, getMavenCentralArtifactIdLink( groupId, artifactId ) );
        }

        private static String getMavenCentralArtifactIdLink( String groupId, String artifactId )
        {
            return getMavenCentralGroupIdLink( groupId ) + "%20AND%20a%3A%22" + artifactId + "%22";
        }

        private String getMavenCentralVersionLinkedName( String groupId, String artifactId, String version )
        {
            return linkedName( version, getMavenCentralVersionLink( groupId, artifactId, version ) );
        }

        private static String getMavenCentralVersionLink( String groupId, String artifactId, String version )
        {
            return getMavenCentralArtifactIdLink( groupId, artifactId ) + "%20AND%20v%3A%22" + version + "%22";
        }

        private String getMavenCentralPackagingLinkName( String groupId, String artifactId, String version,
                String packaging )
        {
            return linkedName( packaging, getMavenCentralPackagingLink( groupId, artifactId, version, packaging ) );
        }

        private static String getMavenCentralPackagingLink( String groupId, String artifactId, String version,
                String packaging )
        {
            return MAVEN_CENTRAL_URL + "#artifactdetails|" + groupId + "|" + artifactId + "|" + version + "|" + packaging;
        }

    }
}
