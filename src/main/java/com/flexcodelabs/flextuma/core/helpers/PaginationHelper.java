package com.flexcodelabs.flextuma.core.helpers;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationHelper {

	public static Pageable getPageable(Integer page, Integer pageSize) {
		int p = getPage(page);
		int s = getPageSize(pageSize);

		return PageRequest.of(p, s, Sort.by("created").descending());
	}

	private static int getPage(Integer page) {
		if (page == null || page < 1) {
			return 0;
		}
		return page;
	}

	private static int getPageSize(Integer pageSize) {
		if (pageSize == null || pageSize < 1) {
			return 15;
		}
		return pageSize;
	}
}
