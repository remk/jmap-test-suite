package jmap

import com.dimafeng.testcontainers.GenericContainer

sealed trait JMAPServer {
    def name : String
    def getContainer(): GenericContainer
  }
  case object Cyrus extends JMAPServer {
    override def name: String = "Cyrus"

    override def getContainer(): GenericContainer = {
      val container = GenericContainer("cyrus-jmap").configure(configProvider => {
        configProvider.addExposedPort(143)
        configProvider.addExposedPort(80)
        configProvider.addExposedPort(25)
      })

      container.underlyingUnsafeContainer.withCreateContainerCmdModifier(cmd => cmd.withHostName("cyrus.domain"))
      container
    }
  }
  case object James extends JMAPServer {
    override def name: String = "James"

    override def getContainer(): GenericContainer = {
      val container = GenericContainer("linagora/james-memory:branch-master").configure(configProvider => {
        configProvider.addExposedPort(143)
        configProvider.addExposedPort(80)
        configProvider.addExposedPort(25)
      })

      container.underlyingUnsafeContainer.withCreateContainerCmdModifier(cmd => cmd.withHostName("cyrus.domain"))
      container
    }
  }