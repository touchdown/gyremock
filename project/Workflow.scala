import sbtghactions.GenerativePlugin.autoImport.{JavaSpec, UseRef, WorkflowJob, WorkflowStep}

/** read up on https://docs.github.com/en/actions/quickstart
  * and https://github.com/djspiewak/sbt-github-actions
  */
object Workflow {

  import WorkflowSteps._

  /** @param scalaVersion
    * a job is run per combo of os, java, scala version in the matrix, concurrently, up to 256
    */
  def build(scalaVersion: String): WorkflowJob = {
    WorkflowJob(
      id = "build",
      name = "Build and Test",
      oses = List("ubuntu-20.04"),
      scalas = List(scalaVersion),
      javas = List(JavaSpec(JavaSpec.Distribution.Temurin, "11")),
      env =
        Map("GITHUB_EVENT_BEFORE" -> "${{ github.event.before }}", "GITHUB_EVENT_AFTER" -> "${{ github.event.after }}"),
      steps = List(cloneAndCheckoutToCurrentBranch, splitJava, setupJava, symlink, test),
      cond = None
    )
  }

  /** @param scalaVersion
    * a job is run per combo of os, java, scala version in the matrix, concurrently, up to 256
    */
  def publish(scalaVersion: String, projVersion: String): WorkflowJob = {
    WorkflowJob(
      id = "publish",
      name = "Docker Publish",
      oses = List("ubuntu-20.04"),
      scalas = List(scalaVersion),
      javas = List(JavaSpec(JavaSpec.Distribution.Temurin, "1.11")),
      steps = List(cloneAndCheckoutToCurrentBranch, setupBuildx, dockerLogin, dockerPublish(projVersion)),
      cond = Some("github.event_name != 'pull_request' && (github.ref == 'refs/heads/main')"),
      needs = List("build")
    )
  }

  /** combine all workflow jobs
    * @param scalaVersion  scala version in current project
    * @param projVersion   project version in current project
    * @return combined workflow jobs
    */
  def apply(scalaVersion: String, projVersion: String): Seq[WorkflowJob] =
    Seq(build(scalaVersion), publish(scalaVersion, projVersion))
}

// all workflow steps are defined here
object WorkflowSteps {

  val cloneAndCheckoutToCurrentBranch: WorkflowStep.Use =
    WorkflowStep.Use(
      name = Some("Clone and checkout to current branch"),
      ref = UseRef.Public(owner = "actions", repo = "checkout", ref = "v2"),
      params = Map("fetch-depth" -> "0")
    )

  val splitJava: WorkflowStep.Use = WorkflowStep.Use(
    id = Some("java_split"),
    name = Some("split Java"),
    ref = UseRef.Public(owner = "jungwinter", repo = "split", ref = "v2"),
    params = Map("msg" -> "${{ matrix.java }}", "separator" -> "@", "maxsplit" -> "2")
  )

  val setupJava: WorkflowStep.Use =
    WorkflowStep.Use(
      name = Some("Setup Java"),
      ref = UseRef.Public(owner = "actions", repo = "setup-java", ref = "v3"),
      params = Map(
        "java-version" -> "${{ steps.java_split.outputs._2 }}",
        "distribution" -> "${{ steps.java_split.outputs._1 }}",
        "cache" -> "sbt"
      )
    )

  val symlink: WorkflowStep.Run =
    WorkflowStep.Run(name = Some("Symlink protobuf"), commands = List("ln -s example/proto proto"))

  val test: WorkflowStep.Sbt =
    WorkflowStep.Sbt(name = Some("Build images"), commands = List("githubWorkflowCheck", "scalafmtCheckAll", "test"))

  val compress: WorkflowStep.Run =
    WorkflowStep.Run(
      name = Some("Compress target directories"),
      commands = List("tar cf targets.tar target project/target")
    )

  val uploadTar: WorkflowStep.Use =
    WorkflowStep.Use(
      name = Some("Upload target directories"),
      ref = UseRef.Public(owner = "actions", repo = "upload-artifact", ref = "v2"),
      params = Map("name" -> "target-${{ matrix.os }}-${{ matrix.scala }}-${{ matrix.java }}", "path" -> "targets.tar")
    )

  val downloadTar: WorkflowStep.Use =
    WorkflowStep.Use(
      name = Some("Download target directories"),
      ref = UseRef.Public(owner = "actions", repo = "download-artifact", ref = "v2"),
      params = Map("name" -> "target-${{ matrix.os }}-${{ matrix.scala }}-${{ matrix.java }}")
    )

  val setupBuildx: WorkflowStep.Use =
    WorkflowStep.Use(
      id = Some("buildx"),
      name = Some("Install buildx"),
      ref = UseRef.Public(owner = "crazy-max", repo = "ghaction-docker-buildx", ref = "v1"),
      params = Map("version" -> "latest")
    )

  val inflate: WorkflowStep.Run =
    WorkflowStep.Run(name = Some("Inflate target directories"), commands = List("tar xf targets.tar", "rm targets.tar"))

  val dockerLogin: WorkflowStep.Run =
    WorkflowStep.Run(
      name = Some("Login to docker hub"),
      commands = List(
        "echo \"${{ secrets.DOCKER_TOKEN }}\" | docker login --username \"${{ secrets.DOCKER_USERNAME }}\" --password-stdin"
      )
    )

  def dockerPublish(projVersion: String): WorkflowStep.Run = WorkflowStep.Run(
    name = Some("Docker publish"),
    commands =
      List(s"docker buildx build -t touchdown/gyremock:$projVersion --platform linux/amd64,linux/arm64 --push .")
  )
}
