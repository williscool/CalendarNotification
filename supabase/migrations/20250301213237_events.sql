CREATE TABLE "android_metadata" (
	"locale" text
);
--> statement-breakpoint
CREATE TABLE "eventsV9" (
	"cid" integer,
	"id" integer,
	"altm" integer,
	"nid" integer,
	"ttl" text,
	"s1" text,
	"estart" integer,
	"eend" integer,
	"istart" integer,
	"iend" integer,
	"loc" text,
	"snz" integer,
	"ls" integer,
	"dsts" integer,
	"clr" integer,
	"rep" integer,
	"alld" integer,
	"ogn" integer,
	"fsn" integer,
	"attsts" integer,
	"oattsts" integer,
	"i1" integer,
	"i2" integer,
	"i3" integer,
	"i4" integer,
	"i5" integer,
	"i6" integer,
	"i7" integer,
	"i8" integer,
	"s2" text,
	PRIMARY KEY("id", "istart")
);
--> statement-breakpoint
CREATE UNIQUE INDEX "eventsIdxV9" ON "eventsV9" ("id","istart");