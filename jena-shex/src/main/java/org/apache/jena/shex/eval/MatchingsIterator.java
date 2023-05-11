package org.apache.jena.shex.eval;

import org.apache.jena.graph.Triple;
import org.apache.jena.shex.expressions.TripleConstraint;

import java.util.*;

/** Starting from a map that with every {@link Triple} associates a collection of matching triple constraints, allows to iterate over all possible ways to match every triple with a unique triple constraint.
 * This is equivalent to computing a Cartesian product of the sets associated with the triples.
 */
public class MatchingsIterator implements Iterator<Map<Triple, TripleConstraint>> {

    /** The triples in the neighbourhood are used as index, their order in the list is important. */
    private final List<Triple> neighbourhood;
    /** allMatches.get(i) contains all triple constraints matched with the triple neighbourhood.get(i) */
    private final List<List<TripleConstraint>> allMatching;

    /** Used for the iteration: sizes[i] = allMatches.get(i).getSize() */
    private final int[] sizes;
    /** Used for the iteration: 0 <= currentIndexes[i] < sizes[i] */
    private final int[] currentIndexes;

    /** Constructs the iterable object starting from the {@param preMatching} map, by restricting it only to those triples in {@param domain}. */
    public MatchingsIterator(Map<Triple, List<TripleConstraint>> preMatching, List<Triple> domain) {
        neighbourhood = new ArrayList<>(domain.size());
        allMatching = new ArrayList<>(domain.size());

        // TODO : iterate over the map entry set / does the domain play a role ?
        for (Triple e : domain) {
            neighbourhood.add(e);
            allMatching.add(preMatching.get(e));
        }
        currentIndexes = new int[allMatching.size()+1]; // Adding an artificial first column allows writing more easily all the operations
        sizes = new int[allMatching.size()+1];
        for (int i = 0; i < currentIndexes.length-1; i++) {
            currentIndexes[i+1] = 0;
            sizes[i+1] = allMatching.get(i).size();
        }
        currentIndexes[0] = 0;
        sizes[0] = 1;
    }

    @Override
    public boolean hasNext() {
        for (int i = 0; i < currentIndexes.length; i++)
            if (currentIndexes[i] >= sizes[i])
                return false;
        return true;
    }

    private void goToNext () {
        int i = currentIndexes.length - 1;
        boolean incrementsToZero = true;
        while (i > 0 && incrementsToZero) {
            currentIndexes[i] = (currentIndexes[i]+1) % sizes[i];
            incrementsToZero = currentIndexes[i]==0;
            i--;
        }
        if (i == 0 && incrementsToZero)
            currentIndexes[0]++;
    }

    @Override
    public Map<Triple, TripleConstraint> next() {
        if (! hasNext())
            throw new NoSuchElementException();

        Map<Triple, TripleConstraint> next = new HashMap<>(currentIndexes.length);
        for (int i = 1; i < currentIndexes.length; i++) {
            next.put(neighbourhood.get(i-1), allMatching.get(i-1).get(currentIndexes[i]));
        }

        goToNext();

        return next;
    }
}
