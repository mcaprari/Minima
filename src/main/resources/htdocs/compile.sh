exit
return 
die
files='
	./js/index.js
'

rm all.js
for f in $files; do
	echo >> all.js
	echo "//$f" >> all.js
	echo >> all.js
	cat $f >> all.js
	echo >> all.js
done 