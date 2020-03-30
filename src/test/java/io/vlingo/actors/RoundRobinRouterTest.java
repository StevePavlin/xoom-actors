// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.
package io.vlingo.actors;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vlingo.actors.testkit.AccessSafely;
import io.vlingo.common.Completes;
import io.vlingo.telemetry.MicrometerTelemetry;
import io.vlingo.telemetry.plugin.mailbox.DefaultMailboxTelemetry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static io.vlingo.telemetry.plugin.mailbox.DefaultMailboxTelemetry.PREFIX;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * SmallestMailboxRouterTest tests {@link SmallestMailboxRouter}.
 */
public class RoundRobinRouterTest extends ActorsTest {
  private static int messageCount = 0;
  private MeterRegistry registry;
  private static DefaultMailboxTelemetry telemetry;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, Clock.SYSTEM);
    telemetry = new DefaultMailboxTelemetry(new MicrometerTelemetry(registry));
  }

  @Test
  public void testTwoArgConsumerProtocol() {
    final int messagesToSend = 3;

    final Results results = new Results(messagesToSend);

    final SubscriptionProtocol router = world.actorFor(
            SubscriptionProtocol.class,
            Definition.has(TestRouterActor.class, Definition.NoParameters));

    final String FirstWorkerAddress = "1f7b13b6-3631-415e-88f2-ea3496f1a70d";
    SubscriptionProtocol FirstWorker = world.actorFor(
            SubscriptionProtocol.class,
            Definition.has(TestRouteeActor.class, Definition.NoParameters, FirstWorkerAddress));

    final String SecondWorkerAddress = "761b8c2e-0cfc-40c8-af97-50eff09fb850";
    SubscriptionProtocol SecondWorker = world.actorFor(
            SubscriptionProtocol.class,
            Definition.has(TestRouteeActor.class, Definition.NoParameters, SecondWorkerAddress));

    final String ThirdWorkerAddress = "2f6d29ca-0313-4e75-86a2-876bbfb6fd29";
    SubscriptionProtocol ThirdWorker = world.actorFor(
            SubscriptionProtocol.class,
            Definition.has(TestRouteeActor.class, Definition.NoParameters, ThirdWorkerAddress));

    router.subscribe(FirstWorker);
    router.subscribe(SecondWorker);
    router.subscribe(ThirdWorker);

    assertPendingMessagesNumberIs(1, FirstWorkerAddress);
    assertPendingMessagesNumberIs(2, SecondWorkerAddress);
    assertPendingMessagesNumberIs(3, ThirdWorkerAddress);

    messageCount = 0;
    for (int i = 0; i < messagesToSend; i++) {
      messageCount++;
      router
              .getWorkerName()
              .andFinallyConsume(answer -> results.access.writeUsing("answers", answer));
    }

    final List<String> allExpected = new ArrayList<>();
    allExpected.add("1f7b13b6-3631-415e-88f2-ea3496f1a70d");
    allExpected.add("761b8c2e-0cfc-40c8-af97-50eff09fb850");
    allExpected.add("2f6d29ca-0313-4e75-86a2-876bbfb6fd29");

    for (int round = 0; round < messagesToSend; round++) {
      String actual = results.access.readFrom("answers", round);
      assertTrue(allExpected.remove(actual));
    }
    assertEquals(0, allExpected.size());
  }

  public interface SubscriptionProtocol {
    void subscribe(SubscriptionProtocol routeeActor);

    void unsubscribe(SubscriptionProtocol routeeActor);

    Completes<String> getWorkerName();
  }

  public static class TestRouterActor extends RoundRobinRouter<SubscriptionProtocol>
          implements SubscriptionProtocol {

    public TestRouterActor() {
      super(new RouterSpecification<>(
              0,
              Definition.has(TestRouteeActor.class, Definition.NoParameters),
              SubscriptionProtocol.class
      ));
    }

    @Override
    public void subscribe(SubscriptionProtocol routeeActor) {
      subscribe(Routee.of(routeeActor));
    }

    @Override
    public void unsubscribe(SubscriptionProtocol routeeActor) {
      unsubscribe(Routee.of(routeeActor));
    }

    @Override
    public Completes<String> getWorkerName() {
      return dispatchQuery((subscriptionProtocol, a1) -> subscriptionProtocol.getWorkerName(), null);
    }
  }

  public static class TestRouteeActor extends Actor implements SubscriptionProtocol {
    TestRouteeActor() {
      Message message;
      final Actor receiver = this;

      message = mock(Message.class);
      doReturn(receiver).when(message).actor();

      messageCount++;
      for (int i = 0; i < messageCount; i++) {
        telemetry.onSendMessage(message.actor());
      }
    }

    @Override
    public void subscribe(SubscriptionProtocol routeeActor) {
    }

    @Override
    public void unsubscribe(SubscriptionProtocol routeeActor) {
    }

    @Override
    public Completes<String> getWorkerName() {
      return completes().with(this.lifeCycle.environment.address.name());
    }
  }

  private void assertPendingMessagesNumberIs(final int expectedMessageCount, String addressOfActor) {
    Gauge byActorPending = registry.get(PREFIX + "TestRouteeActor.pending").tags(singletonList(Tag.of("Address", addressOfActor))).gauge();
    assertEquals(expectedMessageCount, byActorPending.value(), 0);
  }

  public static class Results {
    public AccessSafely access;
    private final String[] answers;
    private int index;

    Results(final int totalAnswers) {
      this.answers = new String[totalAnswers];
      this.index = 0;
      this.access = afterCompleting(totalAnswers);
    }

    private AccessSafely afterCompleting(final int steps) {
      access = AccessSafely
              .afterCompleting(steps)
              .writingWith("answers", (String answer) -> answers[index++] = answer)
              .readingWith("answers", (Integer index) -> answers[index]);
      return access;
    }
  }
}
