package com.github.dmexe.finagle.consul

import com.github.dmexe.finagle.consul.{ConsulSession, Spec}

class ConsulSessionSpec extends Spec {

  "ConsulSession" should {
    "sucessfuly opened and closed" in Deferable { defer =>
      val session = new ConsulSession(client, ConsulSession.Options("spec", ttl = 10, interval = 1, lockDelay = 1))
      defer(session.close())

      Thread.sleep(2000)
      val Some(reply) = session.get
      assert(reply.nonEmpty)
    }

    "restore session when heartbeat lost" in Deferable { defer =>
      val session = new ConsulSession(client, ConsulSession.Options("spec", ttl = 10, interval = 20, lockDelay = 1))
      defer(session.close())

      Thread.sleep(3000)
      val Some(id0) = session.get
      assert(id0.nonEmpty)

      Thread.sleep(20000)
      val Some(id1) = session.get
      assert(id1.nonEmpty)
      assert(id1 != id0)
    }
  }

}
