import BuildKeys._
import Boilerplate._

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import sbtcrossproject.CrossProject

// ---------------------------------------------------------------------------
// Commands

addCommandAlias("ci-test",    ";clean;Test/compile;test;package")
addCommandAlias("ci-doc",     ";unidoc ;site/mdoc")
addCommandAlias("ci",         ";project root ;reload ;+ci-test ;ci-doc")
addCommandAlias("release",    ";+clean ;ci-release ;unidoc ;site/publishMicrosite")

// ---------------------------------------------------------------------------
// Versions

val Scala212 = "2.12.15"
val Scala213 = "2.13.8"
val Scala3   = "3.1.0"

val CatsVersion        = "2.7.0"
val CirceVersionV0_14  = "0.14.1"
val ScalaTestVersion   = "3.2.10"
val Shapeless2xVersion = "2.3.3"
val Shapeless3xVersion = "3.0.2"

// ---------------------------------------------------------------------------

/**
  * Defines common plugins between all projects.
  */
def defaultPlugins: Project ⇒ Project = pr => {
  val withCoverage = sys.env.getOrElse("SBT_PROFILE", "") match {
    case "coverage" => pr
    case _ => pr.disablePlugins(scoverage.ScoverageSbtPlugin)
  }
  withCoverage
    .enablePlugins(AutomateHeaderPlugin)
    .enablePlugins(GitBranchPrompt)
    .disablePlugins(MimaPlugin)
}

lazy val sharedSettings = Seq(
  projectTitle := "Newtypes",
  projectWebsiteRootURL := "https://newtypes.monix.io/",
  projectWebsiteBasePath := "/",
  githubOwnerID := "monix",
  githubRelativeRepositoryID := "newtypes",

  organization := "io.monix",
  scalaVersion := Scala213,
  crossScalaVersions := Seq(Scala212, Scala213, Scala3),

  // https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html
  versionScheme := Some("early-semver"),

  // Turning off fatal warnings for doc generation
  Compile / doc / scalacOptions ~= filterConsoleScalacOptions,

  // Turning off fatal warnings and certain annoyances during testing
  Test / scalacOptions ~= (_ filterNot (Set(
    "-Xfatal-warnings",
    "-Werror",
    "-Ywarn-value-discard",
    "-Wvalue-discard",
  ))),

  // ScalaDoc settings
  autoAPIMappings := true,
  scalacOptions ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath", file(".").getAbsolutePath.replaceAll("[.]$", ""),
    // Debug warnings
    "-Wconf:any:warning-verbose",
  ),

  // Activates doc testing
  doctestTestFramework := DoctestTestFramework.ScalaTest,
  doctestScalaTestVersion := Some(ScalaTestVersion),
  doctestOnlyCodeBlocksMode := true,

  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  // ---------------------------------------------------------------------------
  // Options for testing

  Test / logBuffered := false,
  IntegrationTest / logBuffered := false,

  // ---------------------------------------------------------------------------
  // Options meant for publishing on Maven Central

  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url(projectWebsiteFullURL.value)),
  headerLicense := Some {
    val years = {
      val start = "2021"
      val current = java.time.LocalDate.now().getYear().toString()
      if (start != current) s"$start-$current"
      else start
    }
    HeaderLicense.Custom(
      s"""|Copyright (c) $years the ${projectTitle.value} contributors.
          |See the project homepage at: ${projectWebsiteFullURL.value}
          |
          |Licensed under the Apache License, Version 2.0 (the "License");
          |you may not use this file except in compliance with the License.
          |You may obtain a copy of the License at
          |
          |    http://www.apache.org/licenses/LICENSE-2.0
          |
          |Unless required by applicable law or agreed to in writing, software
          |distributed under the License is distributed on an "AS IS" BASIS,
          |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
          |See the License for the specific language governing permissions and
          |limitations under the License."""
      .stripMargin
    )
  },

  scmInfo := Some(
    ScmInfo(
      url(s"https://github.com/${githubFullRepositoryID.value}"),
      s"scm:git@github.com:${githubFullRepositoryID.value}.git"
    )),

  developers := List(
    Developer(
      id="alexelcu",
      name="Alexandru Nedelcu",
      email="noreply@alexn.org",
      url=url("https://alexn.org")
    )),

  // -- Settings meant for deployment on oss.sonatype.org
  sonatypeProfileName := organization.value,
)

/**
  * Shared configuration across all sub-projects with actual code to be published.
  */
def defaultCrossProjectConfiguration(pr: CrossProject) = {
  val sharedJavascriptSettings = Seq(
    coverageExcludedFiles := ".*",
    // Use globally accessible (rather than local) source paths in JS source maps
    scalacOptions += {
      val tagOrHash = {
        val ver = s"v${version.value}"
        if (isSnapshot.value)
          git.gitHeadCommit.value.getOrElse(ver)
        else
          ver
      }
      val l = (LocalRootProject / baseDirectory).value.toURI.toString
      val g = s"https://raw.githubusercontent.com/${githubFullRepositoryID.value}/$tagOrHash/"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          s"-P:scalajs:mapSourceURI:$l->$g"
        case _ =>
          s"-scalajs-mapSourceURI:$l->$g"
      }
    },
  )

  pr.configure(defaultPlugins)
    .settings(sharedSettings)
    .jsSettings(sharedJavascriptSettings)
    .settings(crossVersionSharedSources)
    .settings(filterOutMultipleDependenciesFromGeneratedPomXml(
      "groupId" -> "org.scoverage".r :: Nil,
    ))
}

lazy val root = project.in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(MimaPlugin)
  .aggregate(
    coreJVM, 
    coreJS, 
    integrationCirceV014JVM,
    integrationCirceV014JS,
  )
  .configure(defaultPlugins)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(unidocSettings(coreJVM))
  .settings(
    // Try really hard to not execute tasks in parallel ffs
    Global / concurrentRestrictions := Tags.limitAll(1) :: Nil,
    // Reloads build.sbt changes whenever detected
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    // Deactivate sbt's linter for some temporarily unused keys
    Global / excludeLintKeys ++= Set(
      IntegrationTest / logBuffered,
      coverageExcludedFiles,
      githubRelativeRepositoryID,
    ),
    // Use Node.js in tests
    Global / scalaJSStage := FastOptStage,
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .dependsOn(coreJVM)
  .dependsOn(integrationCirceV014JVM)
  .settings {
    import microsites._
    Seq(
      micrositeName := projectTitle.value,
      micrositeDescription := "Macro-free helpers for defining newtypes in Scala.",
      micrositeAuthor := "The Monix Developers",
      micrositeTwitterCreator := "@monix",
      micrositeGithubOwner := githubOwnerID.value,
      micrositeGithubRepo := githubRelativeRepositoryID.value,
      micrositeUrl := projectWebsiteRootURL.value.replaceAll("[/]+$", ""),
      micrositeBaseUrl := projectWebsiteBasePath.value.replaceAll("[/]+$", ""),
      micrositeDocumentationUrl := s"${projectWebsiteFullURL.value.replaceAll("[/]+$", "")}/${docsMappingsAPIDir.value}/",
      micrositeGitterChannelUrl := githubFullRepositoryID.value,
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      // https://github.com/47degrees/github4s
      libraryDependencies ++= Seq(
        "com.47deg" %% "github4s" % "0.29.1",
        "io.circe" %%% "circe-parser" % CirceVersionV0_14,
      ),
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFilesOutput := (Compile / resourceManaged).value / "jekyll",
      micrositeConfigYaml := ConfigYml(
        yamlPath = Some((Compile / resourceDirectory).value / "microsite" / "_config.yml")
      ),
      makeSite / mappings ++= Seq(
        ((Compile / resourceDirectory).value / "microsite" / "CNAME") -> "CNAME",
      ),
      micrositeExtraMdFiles := Map(
        file("README.md") -> ExtraMdFileConfig("index.md", "page", Map("title" -> "Home", "section" -> "home", "position" -> "100")),
        file("CHANGELOG.md") -> ExtraMdFileConfig("CHANGELOG.md", "page", Map("title" -> "Change Log", "section" -> "changelog", "position" -> "110")),
        file("CONTRIBUTING.md") -> ExtraMdFileConfig("CONTRIBUTING.md", "page", Map("title" -> "Contributing", "section" -> "contributing", "position" -> "120")),
        file("CODE_OF_CONDUCT.md") -> ExtraMdFileConfig("CODE_OF_CONDUCT.md", "page", Map("title" -> "Code of Conduct", "section" -> "code of conduct", "position" -> "130")),
        file("LICENSE.md") -> ExtraMdFileConfig("LICENSE.md", "page", Map("title" -> "License", "section" -> "license", "position" -> "140")),
      ),
      docsMappingsAPIDir := s"api",
      addMappingsToSiteDir(root / ScalaUnidoc / packageDoc / mappings, docsMappingsAPIDir),
      Compile / sourceDirectory := baseDirectory.value / "src",
      Test / sourceDirectory := baseDirectory.value / "test",
      mdocIn := (Compile / sourceDirectory).value / "mdoc",
      scalacOptions ~= filterConsoleScalacOptions,

      mdocVariables := Map(
        "VERSION" -> version.value,
      ),

      Compile / run := {
        import scala.sys.process._

        val s: TaskStreams = streams.value
        val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")

        val jekyllServe: String = s"jekyll serve --open-url --baseurl ${(Compile / micrositeBaseUrl).value}"

        s.log.info("Running Jekyll...")
        Process(shell :+ jekyllServe, (Compile / micrositeExtraMdFilesOutput).value) !
      },
    )
  }

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .configureCross(defaultCrossProjectConfiguration)
  .jsConfigure(_.disablePlugins(MimaPlugin))
  .settings(
    name := "newtypes-core",
    libraryDependencies ++= Seq(
      // https://typelevel.org/cats/
      "org.typelevel" %%% "cats-core" % CatsVersion % Test,
      // https://github.com/scalatest/scalatest
      "org.scalatest" %%% "scalatest" % ScalaTestVersion % Test,
    ),
    // Version specific dependencies
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          // https://github.com/milessabin/shapeless
          "com.chuusai" %% "shapeless" % Shapeless2xVersion % Test,
        )
      case _ =>
        Seq(
          // https://github.com/typelevel/shapeless-3
          "org.typelevel" %% "shapeless3-test" % Shapeless3xVersion % Test
        )
    }),
  )

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js

// ---
def circeSharedSettings(ver: String) = 
  Seq(
    libraryDependencies ++= Seq(
      // https://circe.github.io/circe/
      "io.circe" %%% "circe-core" % ver,
      "io.circe" %%% "circe-parser" % ver % Test,
      "org.scalatest" %%% "scalatest" % ScalaTestVersion % Test,
    ),  
  ) ++ Seq(Compile, Test).map { sc =>
    (sc / unmanagedSourceDirectories) ++= {
      val base = baseDirectory.value
      val jvmOrJs = base.getName
      val mainOrTest = sc match { case Compile => "main"; case Test => "test" }
      val rootDir = baseDirectory.value.getParentFile().getParentFile
      Seq(
        rootDir / "all" / "shared" / "src" / mainOrTest / "scala",
        rootDir / "all" / jvmOrJs / "src" / mainOrTest / "scala",
      )
    }
  }

lazy val integrationCirceV014 = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("integration-circe/v0.14"))
  .jsConfigure(_.disablePlugins(MimaPlugin))
  .configureCross(defaultCrossProjectConfiguration)
  .dependsOn(core)
  .settings(circeSharedSettings(CirceVersionV0_14))
  .settings(
    name := "newtypes-circe-v0.14",
  )

lazy val integrationCirceV014JVM = integrationCirceV014.jvm
lazy val integrationCirceV014JS  = integrationCirceV014.js
