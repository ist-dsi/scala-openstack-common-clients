organization := "pt.tecnico.dsi"
name := "scala-openstack-common-clients"

// ======================================================================================================================
// ==== Compile Options =================================================================================================
// ======================================================================================================================
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8", "-Dfile.encoding=utf-8")
scalaVersion := "3.3.0"

scalacOptions ++= Seq(
  //"-explain",                       // Explain errors in more detail.
  //"-explain-types",               // Explain type errors in more detail.
  "-indent",                        // Allow significant indentation.
  "-new-syntax",                    // Require `then` and `do` in control expressions.
  //"-rewrite",
  "-feature",                       // Emit warning and location for usages of features that should be imported explicitly.
  "-language:future",               // better-monadic-for
  "-language:implicitConversions",  // Allow implicit conversions
  "-deprecation",                   // Emit warning and location for usages of deprecated APIs.
  "-Wunused:all",                   // Enable or disable specific `unused` warnings
  "-Werror",                        // Fail the compilation if there are any warnings.
  "-Wvalue-discard",
  "-source:future",
  //"-Xsemanticdb",                   // Store information in SemanticDB.
  //"-Xcheck-macros",                 // Check some invariants of macro generated code while expanding macros
  //"-Ycook-comments"                 // Cook the comments (type check `@usecase`, etc.)
)

// These lines ensure that in sbt console or sbt test:console the -Werror is not bothersome.
Compile / console / scalacOptions ~= (_.filterNot(_.startsWith("-Werror")))
Test / console / scalacOptions := (Compile / console / scalacOptions).value

// ======================================================================================================================
// ==== Dependencies ====================================================================================================
// ======================================================================================================================
libraryDependencies ++= Seq("blaze-client", "circe").map { module =>
  "org.http4s"    %% s"http4s-$module"  % "1.0.0-M38"
} ++ Seq(
  "io.circe"      %% "circe-core"       % "0.14.5",
  "org.typelevel" %% "cats-time"        % "0.5.1",
  "org.typelevel" %% "kittens"          % "3.0.0", // For show instances
)

// ======================================================================================================================
// ==== Scaladoc ========================================================================================================
// ======================================================================================================================
git.remoteRepo := s"git@github.com:ist-dsi/${name.value}.git"
val latestReleasedVersion = SettingKey[String]("latest released version")
latestReleasedVersion := git.gitDescribedVersion.value.getOrElse("0.0.1-SNAPSHOT")

// Define the base URL for the Scaladocs for your library. This will enable clients of your library to automatically
// link against the API documentation using autoAPIMappings.
apiURL := Some(url(s"${homepage.value.get}/api/${latestReleasedVersion.value}/"))
autoAPIMappings := true // Tell scaladoc to look for API documentation of managed dependencies in their metadata.
Compile / doc / scalacOptions ++= Seq(
  "-author",      // Include authors.
  "-diagrams",    // Create inheritance diagrams for classes, traits and packages.
  "-groups",      // Group similar functions together (based on the @group annotation)
  "-implicits",   // Document members inherited by implicit conversions.
  "-doc-title", name.value.capitalize,
  "-doc-version", latestReleasedVersion.value,
  "-doc-source-url", s"${homepage.value.get}/tree/v${latestReleasedVersion.value}€{FILE_PATH}.scala",
  "-sourcepath", baseDirectory.value.getAbsolutePath,
)

enablePlugins(GhpagesPlugin, SiteScaladocPlugin)
SiteScaladoc / siteSubdirName := s"api/${version.value}"
ghpagesCleanSite / excludeFilter := AllPassFilter // We want to keep all the previous API versions
val latestFileName = "latest"
val createLatestSymlink = taskKey[Unit](s"Creates a symlink named $latestFileName which points to the latest version.")
createLatestSymlink := {
  import java.nio.file.Files
  // We use ghpagesSynchLocal instead of ghpagesRepository to ensure the files in the local filesystem already exist
  val linkName = (ghpagesSynchLocal.value / "api" / latestFileName).toPath
  val target = new File(latestReleasedVersion.value).toPath
  if (Files.isSymbolicLink(linkName) && Files.readSymbolicLink(linkName) == target) {
    // All good
  } else {
    Files.delete(linkName)
    Files.createSymbolicLink(linkName, target)
  }
}
ghpagesPushSite := ghpagesPushSite.dependsOn(createLatestSymlink).value
ghpagesBranch := "gh-pages"
ghpagesNoJekyll := false
ghpagesPushSite / envVars := Map("SBT_GHPAGES_COMMIT_MESSAGE" -> s"Add Scaladocs for version ${latestReleasedVersion.value}")

// ======================================================================================================================
// ==== Publishing/Release ==============================================================================================
// ======================================================================================================================
publishTo := sonatypePublishTo.value
sonatypeProfileName := organization.value

licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
homepage := Some(url(s"https://github.com/ist-dsi/${name.value}"))
scmInfo := Some(ScmInfo(homepage.value.get, git.remoteRepo.value))
developers ++= List(
  Developer("Lasering", "Simão Martins", "", url("https://github.com/Lasering")),
  Developer("afonsomatos", "Afonso Matos", "", url("https://github.com/afonsomatos")),
)

// Fail the build/release if updates there are updates for the dependencies
// dependencyUpdatesFailBuild := true // http4s-blaze-client hasn't released de M39 yet

releaseUseGlobalVersion := false
releaseNextCommitMessage := s"Setting version to ${ReleasePlugin.runtimeVersion.value} [skip ci]"

releasePublishArtifactsAction := PgpKeys.publishSigned.value // Maven Central requires packages to be signed
import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  releaseStepTask(dependencyUpdates),
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepTask(Compile / doc),
  releaseStepTask(Test / test),
  setReleaseVersion,
  tagRelease,
  releaseStepTask(ghpagesPushSite),
  publishArtifacts,
  releaseStepCommand("sonatypeRelease"),
  pushChanges,
  setNextVersion
)
