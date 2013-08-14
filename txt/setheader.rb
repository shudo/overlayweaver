#!/usr/bin/ruby

# setheader.rb - set a header to files
#
# Set a header to the head of specified files.
# The original file is renamed to <the original name>.orig.
#
# usage: setheader.rb <header file> [file] ...


ORIG_SUFFIX = ".orig"

COMMENT_C = 0
COMMENT_SHARP = 1


def usage
  print "Usage: setheader.rb <header file> [file]\n"
  exit
end


def writeHeader(fout, header_filename, comment_style)
  if comment_style == COMMENT_C
    fout.puts("/*")
  end

  open(header_filename, "r") do |f|
    while f.gets
      if comment_style == COMMENT_SHARP
	fout.write("# ")
      elsif comment_style == COMMENT_C
	fout.write(" *")
	if $_ !~ /^\s*$/
	  fout.write(" ")
	end
      end

      # write the read line
      fout.write($_)
    end
  end

  if comment_style == COMMENT_C
    fout.puts(" */")
  end
end


def setHeader(target_filename, header_filename)
  comment_style = COMMENT_C

  new_filename = target_filename + ".new"

  open(target_filename, "r") do |fin|
    open(new_filename, "w") do |fout|

      # skip empty lines
      while line = fin.gets
	break if line !~ /^\s*$/
	fout.write(line)
      end

      if line =~/^\s*#!/
	# '#!' found
#p "#! found."
	comment_style = COMMENT_SHARP
	fout.write(line)

	# skip empty lines
	while line = fin.gets
	  break if line !~ /^\s*$/
	  fout.write(line)
	end
      end

      if line =~ /^\s*\/\*/
#p "/* found."
	# skip comments between '/*' and '*/'
	while line = fin.gets
	  break if line =~ /\*\//
	end
#p "*/ found."
	line = fin.gets
      elsif line =~ /\s*# /
	# skip comments following '#'
	while line = fin.gets
	  break if line !~ /\s*#/
	end
      end

      # copy the header
      writeHeader(fout, header_filename, comment_style)

      if (line != nil) && (line !~ /^\s*$/)
	# write an empty line
	fout.puts
      end

      # copy body of the original file
      begin
	fout.write(line)
      end while line = fin.gets
    end
  end

  File.rename(target_filename, target_filename + ORIG_SUFFIX)
  File.rename(new_filename, target_filename)
end


# main program

if ARGV.size < 2
  usage
end

header_filename = ARGV.shift

ARGV.each {|fname|
  print("Process the file: ", fname, "\n")
  setHeader(fname, header_filename)
}
