bindir=/c/Users/kenny/Desktop/webportal/spec2006x86/O2
outdir=/c/Users/kenny/Desktop/webportal/spec2006x86/O2_out
ghidradir=/c/Users/kenny/Desktop/ghidra_10.2.3_PUBLIC
scriptdir=/c/Users/kenny/Desktop/webportal/ghidra_scripts
projdir=/c/Users/kenny/Desktop/webportal/ghidra
decompdir=/c/Users/kenny/Desktop/webportal/spec2006x86/decompiled
mkdir $decompdir
cp $scriptdir/json-simple-1.1.1.jar  $ghidradir/Ghidra/patch/
for binary in bzip2
do
        start=$(date +%s)
        cd $scriptdir
	$ghidradir/support/analyzeHeadless $projdir utils -import $bindir/$binary -deleteProject -overwrite -scriptPath $scriptdir -postScript IndirectCallTargetResolving.java -preScript SetAutoAnalysisOptions.java
        end=$(date +%s)
        mkdir -p $outdir/$binary
        echo "Elapsed Time: $(($end-$start)) seconds" > $outdir/$binary/out
	mv $decompdir/$binary.json $outdir/$binary
done
