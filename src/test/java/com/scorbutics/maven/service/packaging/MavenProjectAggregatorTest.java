package com.scorbutics.maven.service.packaging;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MavenProjectAggregator to verify it correctly aggregates///////////////
 * projects from Maven session and external POMs.
 */
class MavenProjectAggregatorTest {

    @Mock
    private ProjectBuilder projectBuilder;

    @Mock
    private MavenSession session;

    @Mock
    private Log logger;

    @Mock
    private MavenProject sessionProject1;

    @Mock
    private MavenProject sessionProject2;

    private MavenProjectAggregator aggregator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aggregator = new MavenProjectAggregator(projectBuilder, session, logger);
    }

    @Test
    void testAggregateProjects_withNullExternalPath_returnsOnlySessionProjects() throws MojoExecutionException {
        // Given
        final List<MavenProject> sessionProjects = Arrays.asList(sessionProject1, sessionProject2);
        when(session.getProjects()).thenReturn(sessionProjects);

        // When
        final List<MavenProject> result = aggregator.aggregateProjects(null);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(sessionProject1));
        assertTrue(result.contains(sessionProject2));
        verify(logger).debug(contains("Adding 2 project(s) from Maven reactor session"));
        verify(logger).debug(contains("Total projects aggregated: 2"));
    }

    @Test
    void testAggregateProjects_withEmptySessionProjects_returnsEmptyList() throws MojoExecutionException {
        // Given
        when(session.getProjects()).thenReturn(Arrays.asList());

        // When
        final List<MavenProject> result = aggregator.aggregateProjects(null);

        // Then
        assertTrue(result.isEmpty());
        verify(logger).debug(contains("Total projects aggregated: 0"));
    }

    @Test
    void testAggregateProjects_withNullSessionProjects_returnsEmptyList() throws MojoExecutionException {
        // Given
        when(session.getProjects()).thenReturn(null);

        // When
        final List<MavenProject> result = aggregator.aggregateProjects(null);

        // Then
        assertTrue(result.isEmpty());
        verify(logger).debug(contains("Total projects aggregated: 0"));
    }

    /**
     * Note: Full integration test with external POM reading would require
     * actual POM files and ProjectBuilder setup, which is more suitable
     * for integration tests rather than unit tests.
     */
}
