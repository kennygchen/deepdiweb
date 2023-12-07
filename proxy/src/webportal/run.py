import os
import subprocess
import time
from argparse import ArgumentParser, ArgumentDefaultsHelpFormatter

def decompile(bindir, outdir, ghidradir, scriptdir, projdir, decompdir):
    os.makedirs(decompdir, exist_ok=True)
    os.system(f"cp {scriptdir}/json-simple-1.1.1.jar  {ghidradir}/Ghidra/patch/")
    binaries = ["bzip2"]
    for binary in binaries:
        start_time = time.time()
        os.chdir(scriptdir)
        ghidra_cmd = ghidradir + "/support/analyzeHeadless " + projdir + " utils -import " + bindir + " -deleteProject -overwrite -scriptPath " + scriptdir + " -postScript IndirectCallTargetResolving.java -preScript SetAutoAnalysisOptions.java"
        # ghidre_cmd = [
        #     f"{ghidradir}/support/analyzeHeadless",
        #     projdir,
        #     "utils",
        #     "-import",
        #     bindir,
        #     "-deleteProject",
        #     "-overwrite",
        #     "-scriptPath",
        #     scriptdir,
        #     "-postScript",
        #     "IndirectCallTargetResolving.java",
        #     "-preScript",
        #     "SetAutoAnalysisOptions.java"
        # ]
        # subprocess.run(ghidre_cmd, capture_output=True)
        os.system(ghidra_cmd)
        end_time = time.time()
        os.makedirs(f"{outdir}/{binary}", exist_ok=True)
        
        with open(f"{outdir}/{binary}/out", "w") as f:
            f.write(f"Elapsed Time: {int(end_time - start_time)} seconds")

        # os.rename(f"{decompdir}/{binary}.json", f"{outdir}/{binary}/{binary}.json")
    print("Finished processing all binaries.")

def main():
    parser = ArgumentParser(formatter_class=ArgumentDefaultsHelpFormatter, conflict_handler='resolve')
    parser.add_argument('--bindir', required=True, help='Specify the binary file directory')
    parser.add_argument('--outdir', required=True, help='Specify the output directory')
    parser.add_argument('--ghidradir', required=True, help='Home directory of Ghidra')
    parser.add_argument('--scriptdir', required=True, help='Specify the ghidra scripts directory') 
    parser.add_argument('--projdir', required=True, help='Specify the project directory')
    parser.add_argument('--decompdir', required=True, help='Specify the decompile output directory')
    args = parser.parse_args()
    bindir = args.bindir
    outdir = args.outdir
    ghidradir = args.ghidradir
    scriptdir = args.scriptdir
    projdir = args.projdir
    decompdir = args.decompdir
    
    decompile(bindir, outdir, ghidradir, scriptdir, projdir, decompdir)


if __name__ == "__main__":
    main()