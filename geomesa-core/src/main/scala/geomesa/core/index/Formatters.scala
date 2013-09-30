/*
 * Copyright 2013 Commonwealth Computer Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geomesa.core.index

import org.apache.hadoop.io.Text
import org.joda.time.DateTimeZone
import util.Random

trait TextFormatter[E <: IndexEntry] {
  def format(entry: E): Text
  def numBits: Int
}

object TextFormatter {
  implicit def string2Text(s: String): Text = new Text(s)
}

/**
 * These GeoHash strings are padded to 7 characters with a period.  This is
 * done for a few reasons:
 * 1.  with the addition of GeoHash decomposition for non-point data, some
 *     of the GeoHashes will be at fewer than 35 bits (but always on 5-bit
 *     boundaries);
 * 2.  a period appears earlier in the ASCII chart than do any of the alpha-
 *     numeric characters
 *
 * @param offset how many characters (from the left) to skip
 * @param numBits how many characters to use
 */
case class GeoHashTextFormatter(offset: Int, numBits: Int) extends TextFormatter[SpatioTemporalIndexEntry] {
  def format(entry: SpatioTemporalIndexEntry) = new Text(entry.gh.hash.padTo(7,".").mkString.drop(offset).take(numBits))
}

// note:  this will fail if you have an entry lacking a valid date
case class DateTextFormatter(f: String) extends TextFormatter[SpatioTemporalIndexEntry] {
  val timeZone = DateTimeZone.forID("UTC")
  val numBits = f.length
  val formatter = org.joda.time.format.DateTimeFormat.forPattern(f)
  def format(entry: SpatioTemporalIndexEntry) = new Text(formatter.print(
    entry.dt.get.withZone(timeZone)))
}

// the intent is that "%99#r" will mean:  create shards from 0..99
case class PartitionTextFormatter[E <: IndexEntry](numPartitions: Int) extends TextFormatter[E] {
  val numBits: Int = numPartitions.toString.length
  val fmt = ("%0" + numBits + "d").format(_: Int)
  def getRandomPartion = Random.nextInt(numPartitions + 1)
  def format(entry: E): Text = new Text(""+fmt(getRandomPartion))
}

case class ConstantTextFormatter[E <: IndexEntry](constStr: String) extends TextFormatter[E] {
  val constText = new Text(constStr)
  def format(entry: E) = constText
  def numBits = constStr.length
}

case class IdFormatter(maxLength: Int) extends TextFormatter[SpatioTemporalIndexEntry] {
  def format(entry: SpatioTemporalIndexEntry): Text = new Text(entry.sid.padTo(maxLength, "_").mkString)
  def numBits: Int = maxLength
}

case class CompositeTextFormatter[E <: IndexEntry](lf: Seq[TextFormatter[E]], sep: String) extends TextFormatter[E] {
  val numBits = lf.map(_.numBits).sum
  def format(entry: E) = new Text(lf.map { _.format(entry) }.mkString(sep))
}


