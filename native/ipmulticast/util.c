/*
 * Copyright 2006 National Institute of Advanced Industrial Science
 * and Technology (AIST), and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "common.h"

#ifndef LOG_BUF_SIZE
#  define LOG_BUF_SIZE 512
#endif

void log(int severity, int syserr, char *format, ...) {
	va_list argp;
	char formatted[LOG_BUF_SIZE];

	va_start(argp, format);
	vsnprintf(formatted, LOG_BUF_SIZE, format, argp);
	va_end(argp);

	fprintf(stderr, "log: %s\n", formatted);
}
