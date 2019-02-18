// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.actors;

import io.vlingo.common.Scheduled;

public class Scheduled__Proxy<T> implements io.vlingo.common.Scheduled<T> {

  private static final String intervalSignalRepresentation1 = "intervalSignal(io.vlingo.common.Scheduled<T>, T)";

  private final Actor actor;
  private final Mailbox mailbox;

  public Scheduled__Proxy(final Actor actor, final Mailbox mailbox){
    this.actor = actor;
    this.mailbox = mailbox;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void intervalSignal(io.vlingo.common.Scheduled<T> arg0, T arg1) {
    if (!actor.isStopped()) {
      final java.util.function.Consumer<Scheduled> consumer = (actor) -> actor.intervalSignal(arg0, arg1);
      if (mailbox.isPreallocated()) { mailbox.send(actor, Scheduled.class, consumer, null, intervalSignalRepresentation1); }
      else { mailbox.send(new LocalMessage<Scheduled>(actor, Scheduled.class, consumer, intervalSignalRepresentation1)); }
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, intervalSignalRepresentation1));
    }
  }
}
