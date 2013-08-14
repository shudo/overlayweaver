#!/usr/bin/ruby

# remove-comment.rb - remove comment and space lines from an input.

in_comment = false

while line = gets
  line.chomp!

  # remove spaces at the end of a line
#  line =~ /(.*\S)\s*/
#  line = $1

  # a comment closed with "*/"
  if in_comment
    if line =~ /\*\/(.*)/
      line = $1
      in_comment = false;
    end
  end

  if !in_comment
    if line =~ /(.*)\/\*.*\*\/(.*)/	# remove /* ... */
      line = $1 + $2
    elsif line =~ /(.*)\/\*/		# a comment opend with "/*"
      line = $1
      in_comment = true;
    end

    # remove a comment with "//"
    if line =~ /(.*)\/\//
      line = $1
    end

    if line =~ /\S/
      print line, "\n"
    end
  end
end
