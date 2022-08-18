import sbtghactions.GenerativePlugin.autoImport.{JavaSpec, UseRef, WorkflowJob, WorkflowStep}

/** read up on https://docs.github.com/en/actions/quickstart
  * and https://github.com/djspiewak/sbt-github-actions
  */
object Workflow {

  import WorkflowSteps._

  /** @param scalaVersion
    * a job is run per combo of os, java, scala version in the matrix, concurrently, up to 256
    */
  def validateJob(scalaVersion: String): WorkflowJob = {
    WorkflowJob(
      id = "ci",
      name = "ci",
      oses = List("ubuntu-20.04"),
      scalas = List(scalaVersion),
      javas = List(JavaSpec(JavaSpec.Distribution.Temurin, "1.11")),
      env = Map(
        "DOCKER_TOKEN" -> "${{ secrets.DOCKER_TOKEN }}",
        "DOCKER_USERNAME" -> "${{ secrets.DOCKER_USERNAME }}",
        "GITHUB_EVENT_BEFORE" -> "${{ github.event.before }}",
        "GITHUB_EVENT_AFTER" -> "${{ github.event.after }}"
      ),
      // combine all workflow steps
      steps = List(cloneAndCheckoutToCurrentBranch, setupScala, runValidation),
      cond = None
    )
  }

  /** combine all workflow jobs
    *
    * @param scalaVersion
    * scala version in current project
    * @return
    * combined workflow jobs
    */
  def apply(scalaVersion: String): Seq[WorkflowJob] = Seq(validateJob(scalaVersion))
}

// all workflow steps are defined here
object WorkflowSteps {

  val cloneAndCheckoutToCurrentBranch: WorkflowStep.Use =
    WorkflowStep.Use(
      name = Some("Clone and checkout to current branch"),
      ref = UseRef.Public(owner = "actions", repo = "checkout", ref = "v2"),
      params = Map("fetch-depth" -> "0", "submodules" -> "true")
    )

  val setupScala: WorkflowStep.Use =
    WorkflowStep.Use(
      name = Some("Setup Scala"),
      ref = UseRef.Public(owner = "olafurpg", repo = "setup-scala", ref = "v13"),
      params = Map("java-version" -> "${{ matrix.java }}")
    )

  val setupBuildx: WorkflowStep.Use =
    WorkflowStep.Use(
      id = Some("buildx"),
      name = Some("install buildx"),
      ref = UseRef.Public(owner = "crazy-max", repo = "ghaction-docker-buildx", ref = "v1"),
      params = Map("version" -> "latest")
    )

  val runValidation: WorkflowStep.Sbt =
    WorkflowStep.Sbt(name = Some("build images"), commands = List("scalafmtCheckAll", "test"))

  val publish: WorkflowStep.Run = WorkflowStep.Run(
    name = Some("publish"),
    commands = List("docker buildx build", s"--tag touchdown/gyremock:0.3.1", "--platform linux/amd64,linux/arm64 .")
  )
}
