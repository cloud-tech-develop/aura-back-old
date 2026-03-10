package com.cloud_technological.aura_pos.utils;



import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PageableDto<T> {
	@NotNull(message = "El campo 'page' es obligatorio")
	@Digits(integer = Integer.MAX_VALUE, fraction = 0, message = "El campo 'page' debe contener solo números")
	private Long page;

	@NotNull(message = "El campo 'rows' es obligatorio")
	@Digits(integer = Integer.MAX_VALUE, fraction = 0, message = "El campo 'rows' debe contener solo números")
	private Long rows;

	private String search;

	private String order_by;

	private String order;

	private T params;

}