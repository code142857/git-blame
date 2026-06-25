package com.jiangjinghong.git.blame.settings;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 行内 blame 的日期显示格式。
 */
public enum DateFormatMode {

	/** 相对时间，如 "3 days ago" */
	RELATIVE("Relative (e.g. 3 days ago)"),
	/** 仅日期 */
	ISO_DATE("yyyy-MM-dd"),
	/** 日期 + 时分 */
	ISO_DATETIME("yyyy-MM-dd HH:mm"),
	/** 日期 + 时分秒 */
	ISO_DATETIME_WITH_SECONDS("yyyy-MM-dd HH:mm:ss");

	private static final DateTimeFormatter ISO_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
		.withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter ISO_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter ISO_DATETIME_WITH_SECONDS_FMT = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private final String displayName;

	DateFormatMode(@NotNull String displayName) {
		this.displayName = displayName;
	}

	@NotNull
	public String getDisplayName() {
		return displayName;
	}

	@NotNull
	public String format(long epochSeconds) {
		if (epochSeconds <= 0) {
			return "";
		}
		Instant instant = Instant.ofEpochSecond(epochSeconds);
		switch (this) {
			case RELATIVE:
				return formatRelative(instant);
			case ISO_DATE:
				return ISO_DATE_FMT.format(instant);
			case ISO_DATETIME:
				return ISO_DATETIME_FMT.format(instant);
			case ISO_DATETIME_WITH_SECONDS:
				return ISO_DATETIME_WITH_SECONDS_FMT.format(instant);
			default:
				return ISO_DATETIME_FMT.format(instant);
		}
	}

	@NotNull
	private static String formatRelative(@NotNull Instant then) {
		Duration d = Duration.between(then, Instant.now());
		long sec = d.getSeconds();
		if (sec < 0) {
			return "in the future";
		}
		if (sec < 60) {
			return sec + "s ago";
		}
		long min = sec / 60;
		if (min < 60) {
			return min + "m ago";
		}
		long hour = min / 60;
		if (hour < 24) {
			return hour + "h ago";
		}
		long day = hour / 24;
		if (day < 7) {
			return day + "d ago";
		}
		long week = day / 7;
		if (week < 5) {
			return week + "w ago";
		}
		long month = day / 30;
		if (month < 12) {
			return month + "mo ago";
		}
		long year = day / 365;
		return year + "y ago";
	}

	@NotNull
	public static DateFormatMode fromString(@NotNull String value) {
		try {
			return DateFormatMode.valueOf(value);
		}
		catch (IllegalArgumentException e) {
			return RELATIVE;
		}
	}
}
