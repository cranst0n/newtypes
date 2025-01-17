val ScalaJSVersion = Option(System.getenv("SCALAJS_VERSION")).filter(_.nonEmpty).getOrElse("1.8.0")

addSbtPlugin("com.47deg"                 % "sbt-microsites"                % "1.3.4")
addSbtPlugin("com.eed3si9n"              % "sbt-unidoc"                    % "0.4.3")
addSbtPlugin("com.github.sbt"            % "sbt-ci-release"                % "1.5.9")
 addSbtPlugin("com.github.tkawachi"      % "sbt-doctest"                   % "0.9.9")
addSbtPlugin("com.typesafe"              % "sbt-mima-plugin"               % "1.0.1")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"                    % "5.6.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"                  % "0.1.20")
addSbtPlugin("org.portable-scala"        % "sbt-scala-native-crossproject" % "1.1.0")
addSbtPlugin("org.portable-scala"        % "sbt-scalajs-crossproject"      % "1.1.0")
addSbtPlugin("org.scala-js"              % "sbt-scalajs"                   % ScalaJSVersion)
addSbtPlugin("org.scalameta"             % "sbt-mdoc"                      % "2.2.24")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"                 % "1.9.3")
