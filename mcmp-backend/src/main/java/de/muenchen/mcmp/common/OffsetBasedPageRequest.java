
package de.muenchen.mcmp.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class OffsetBasedPageRequest implements Pageable {

    private final int offset;
    private final int limit;

    private Sort sort;

    public OffsetBasedPageRequest(int offset, int limit, Sort sort) {
        if (offset < 0)
            throw new IllegalArgumentException("Offset must not be less than zero!");

        if (limit < -1)
            throw new IllegalArgumentException("Limit must not be less than -1!");

        this.offset = offset;
        this.limit = limit;
        this.sort = sort != null ? sort : Sort.unsorted();
    }

    public OffsetBasedPageRequest(int offset, int limit) {
        this(offset, limit, null);
    }

    @Override
    public int getPageNumber() { return limit == 0 ? 0 : offset / limit; }

    @Override
    public int getPageSize() { return limit; }

    @Override
    public long getOffset() { return offset; }

    @Override
    public Sort getSort() { return this.sort; }

    @Override
    public Pageable next() { return new OffsetBasedPageRequest(offset + limit, limit, sort); }

    @Override
    public Pageable previousOrFirst() { return this; }

    @Override
    public Pageable first() { return this; }

    @Override
    public Pageable withPage(int pageNumber) { return new OffsetBasedPageRequest(pageNumber * limit, limit, sort); }

    @Override
    public boolean hasPrevious() { return offset > 0; }
}