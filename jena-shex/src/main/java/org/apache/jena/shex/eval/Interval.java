package org.apache.jena.shex.eval;

public class Interval {

    /** These values determine a unique representation of an empty interval. */
    public static final int MIN_EMPTY = 2;
    public static final int MAX_EMPTY = 1;

    public static final int UNBOUND = Integer.MAX_VALUE;

    public static Interval STAR = new Interval(0, UNBOUND);
    public static Interval PLUS = new Interval(1, UNBOUND);
    public static Interval OPT = new Interval(0,1);
    public static Interval EMPTY = new Interval(MIN_EMPTY, MAX_EMPTY);
    public static Interval ONE = new Interval(1,1);
    public static Interval ZERO = new Interval(0,0);

    public final int min, max;

    public Interval (int min, int max) {
        if (min < 0) throw new IllegalArgumentException("Interval negative min bound not allowed");
        if (max < 0) throw new IllegalArgumentException("Interval negative max bound not allowed");
        if (max < min) {
            this.min = MIN_EMPTY; this.max = MAX_EMPTY;
        } else {
            this.min = min; this.max = max;
        }
    }

    public boolean isUnbound () {
        return max == UNBOUND;
    }

    public boolean contains(int i) {
        return i >= min && i <= max;
    }

    /*package*/ static Interval add (Interval i1, Interval i2) {
        int imin, imax;

        imin = i1.min + i2.min;
        imax = (i1.max == Interval.UNBOUND || i2.max == Interval.UNBOUND) ? Interval.UNBOUND : i1.max + i2.max;

        return new Interval(imin, imax);
    }

    /*package*/ static Interval inter (Interval i1, Interval i2) {
        int imin, imax;

        imin = Math.max(i1.min, i2.min);
        imax = Math.min(i1.max, i2.max);

        return new Interval(imin, imax);
    }

    /** This function relies on the fact that the empty interval is represented by [2;1],
     * thus card.max() cannot be equal to 0 except if the interval is [0;0]
     *
     * @param nbOcc
     * @param card
     * @return
     */
    /*package*/ static Interval div(int nbOcc, Interval card) {

        if (card.equals(Interval.ZERO))
            return nbOcc == 0 ? Interval.STAR : Interval.EMPTY;

        int imin, imax;

        // imin = nbOcc / card.imax();   uppper bound
        // with upper bound of (0 / UNBOUND) = 0
        // and  upper bound of (n / UNBOUND) = 1 for n != 0
        if (card.max == Interval.UNBOUND)
            imin = nbOcc == 0 ? 0 : 1;
        else
            imin = nbOcc % card.max == 0 ? nbOcc / card.max : (nbOcc / card.max) + 1;

        // imax = nbOcc / card.imin();  lower bound
        // with lower bound of (0 / 0)
        // and  lower bound of (n / 0) = UNBOUND for n != 0
        imax = card.min == 0 ? Interval.UNBOUND : nbOcc / card.min;

        return new Interval(imin,imax);
    }


    @Override
    public String toString() {
        return String.format("[%d; %s]", min, max == UNBOUND ? "*" : max);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + max;
        result = prime * result + min;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Interval other = (Interval) obj;
        if (max != other.max)
            return false;
        if (min != other.min)
            return false;
        return true;
    }


}