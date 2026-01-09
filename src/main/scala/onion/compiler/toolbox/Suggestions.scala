/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

/**
 * Utility for suggesting similar names when an identifier is not found.
 * Uses Levenshtein distance to find similar candidates.
 */
object Suggestions {

  /**
   * Find the most similar name from a list of candidates.
   *
   * @param name The name that was not found
   * @param candidates Available names to compare against
   * @param maxDistance Maximum edit distance to consider (default: 3)
   * @return Some(similarName) if a close match is found, None otherwise
   */
  def findSimilar(name: String, candidates: Seq[String], maxDistance: Int = 3): Option[String] = {
    if (name.isEmpty || candidates.isEmpty) return None

    val scored = candidates.map { candidate =>
      (candidate, levenshteinDistance(name.toLowerCase, candidate.toLowerCase))
    }

    scored
      .filter { case (_, distance) => distance <= maxDistance && distance > 0 }
      .sortBy { case (candidate, distance) =>
        // Prefer shorter edit distances, then shorter names
        (distance, candidate.length)
      }
      .headOption
      .map(_._1)
  }

  /**
   * Find up to n similar names from a list of candidates.
   */
  def findSimilarMany(name: String, candidates: Seq[String], maxResults: Int = 3, maxDistance: Int = 3): Seq[String] = {
    if (name.isEmpty || candidates.isEmpty) return Seq.empty

    candidates
      .map(c => (c, levenshteinDistance(name.toLowerCase, c.toLowerCase)))
      .filter { case (_, d) => d <= maxDistance && d > 0 }
      .sortBy { case (c, d) => (d, c.length) }
      .take(maxResults)
      .map(_._1)
  }

  /**
   * Compute the Levenshtein (edit) distance between two strings.
   * This is the minimum number of single-character edits (insertions,
   * deletions, or substitutions) required to change one string into the other.
   */
  def levenshteinDistance(s1: String, s2: String): Int = {
    val m = s1.length
    val n = s2.length

    // Handle empty strings
    if (m == 0) return n
    if (n == 0) return m

    // Use two rows instead of full matrix for space efficiency
    var prevRow = Array.tabulate(n + 1)(identity)
    var currRow = new Array[Int](n + 1)

    for (i <- 1 to m) {
      currRow(0) = i
      for (j <- 1 to n) {
        val cost = if (s1(i - 1) == s2(j - 1)) 0 else 1
        currRow(j) = math.min(
          math.min(
            prevRow(j) + 1,      // deletion
            currRow(j - 1) + 1   // insertion
          ),
          prevRow(j - 1) + cost  // substitution
        )
      }
      // Swap rows
      val temp = prevRow
      prevRow = currRow
      currRow = temp
    }

    prevRow(n)
  }

  /**
   * Format a suggestion message if a similar name is found.
   */
  def formatSuggestion(name: String, candidates: Seq[String]): Option[String] = {
    findSimilar(name, candidates).map { similar =>
      Message("suggestion.didYouMean", similar)
    }
  }
}
