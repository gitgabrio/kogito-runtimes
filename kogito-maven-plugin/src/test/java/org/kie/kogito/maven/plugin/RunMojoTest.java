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
package org.kie.kogito.maven.plugin;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.junit5.InjectMojo;
import org.apache.maven.plugin.testing.junit5.MojoTest;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;

import static org.mockito.Mockito.mock;

@MojoTest
class RunMojoTest {

    private static final KogitoBuildContext kogitoBuildContextMocked = mock(KogitoBuildContext.class);
    private static final ClassLoader classLoaderMocked = mock(ClassLoader.class);
    private static final Log logMocked = mock(Log.class);

    @Test
    @InjectMojo(goal = "run", pom = "src/test/resources/unit/run/pom.xml")
    void run(RunMojo mojo) throws MojoExecutionException, MojoFailureException {
        commonSetup(mojo);
        //        mojo.execute();
        //        try (MockedStatic<RunMojo> generateModelHelperMockedStatic = mockStatic(RunMojo.class)) {
        //            mojo.execute();
        //            generateModelHelperMockedStatic.verify(() -> GenerateModelHelper.generateModelFiles(kogitoBuildContextMocked, false), times(1));
        //        }
    }

    private void commonSetup(RunMojo mojo) {
        mojo.outputDirectory = new File(mojo.project.getModel().getBuild().getOutputDirectory());
        mojo.baseDir = mojo.project.getBasedir();
        mojo.projectDir = mojo.project.getBasedir();
    }
}
