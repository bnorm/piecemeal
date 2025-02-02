// RENDER_DIAGNOSTICS_FULL_TEXT

import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Thing<T1 : Any, out T2 : T1, in T3>(
  val t1: T1,
  val t2: T2,
) : Comparable<T3> where T3 : Comparable<T3>, T3 : CharSequence {
  override fun compareTo(other: T3): Int = 0
}

@Piecemeal
class Producer<out T>(
  val value: T
)

@Piecemeal
class Consumer<in T>(
  private val value: T
) {
  fun consume(value: T) {}
}

fun OutOfBoundsMutable() {
  Thing.Mutable<<!UPPER_BOUND_VIOLATED!>String?<!>, <!UPPER_BOUND_VIOLATED!>CharSequence<!>, <!UPPER_BOUND_VIOLATED!>Any<!>>()
}

fun OutOfBoundsBuild() {
  Thing.build<<!UPPER_BOUND_VIOLATED!>String?<!>, <!UPPER_BOUND_VIOLATED!>CharSequence<!>, <!UPPER_BOUND_VIOLATED!>Any<!>> <!ARGUMENT_TYPE_MISMATCH!>{}<!>
}

fun NoArgsToMutable() {
  val thing = Thing.build<String, String, String> {}
  thing.toMutable<!WRONG_NUMBER_OF_TYPE_ARGUMENTS!><String?, CharSequence, Any><!>()
}

fun NoArgsCopy() {
  val thing = Thing.build<String, String, String> {}
  thing.copy<!WRONG_NUMBER_OF_TYPE_ARGUMENTS!><String?, CharSequence, Any><!> {}
}

fun InvariantProducer() {
  val producer = Producer.build<String> {}
  val producerMutable: Producer.Mutable<CharSequence> = <!INITIALIZER_TYPE_MISMATCH, TYPE_MISMATCH!>producer.toMutable()<!>

  val producer2: Producer<CharSequence> = producer
  val producerMutable2: Producer.Mutable<CharSequence> = producer2.toMutable()
}

fun InvariantConsumer() {
  val consumer = Consumer.build<CharSequence> {}
  val consumerMutable: Consumer.Mutable<String> = <!INITIALIZER_TYPE_MISMATCH, TYPE_MISMATCH!>consumer.toMutable()<!>

  val consumer2: Consumer<String> = consumer
  val consumerMutable2: Consumer.Mutable<String> = consumer2.toMutable()
}
