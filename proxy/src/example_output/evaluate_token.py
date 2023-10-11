from collections import defaultdict
import os
import re
import traceback
def load_nodelabel(filepath):
    with open(filepath, "r") as f:
        lines = f.readlines()
    groundtruth = {}
    nodelines = {}
    funcname = None
    func_features = defaultdict(set)
    node2func = dict()
    func_nodes = {}
    for line in lines:
        line = line.strip()
        if line.startswith("#"):
            funcname = line[1:] 
        else:
            features = line.split("|&|")
            groundtruth[features[0]] = features[-1]
            nodelines[features[0]] = features[1:]
            func_features[funcname].add(features[0])
            node2func[features[0]] = funcname

    return groundtruth, nodelines, func_features



def read_decompiled_lines(path):
    with open(path, 'r') as f:
        lines = f.readlines()
    filted_lines = []

    if lines[0].startswith("#include"):
        i = 1
        for line in lines[3:]:
            line = line.strip()
            filted_lines.append(line)
    else:
        for i, line in enumerate(lines):
            idx = line.find(': ')
            filted_lines.append(line[idx+1:].strip())
    return filted_lines

row_template = """
  <tr>
      <td class="diff_lineno" width="auto">%s</td>
      <td class="diff_play" nowrap width="45%%">%s</td>
      <td class="diff_lineno" width="auto">%s</td>
      <td class="diff_play" nowrap width="45%%">%s</td>
  </tr>
  """
html_template = """
  <html>
  <head>
  <style>%(style)s</style>
  </head>
  <body>
  <table class="diff_tab" cellspacing=0>
  %(rows)s
  </table>
  </body>
  </html>
  """
style = """
  table.diff_tab {
    font-family: Courier, monospace;
    table-layout: fixed;
    width: 100%;
  }
  table td {
    white-space: nowrap;
    overflow: hidden;
  }
  .diff_add {
    background-color: #aaffaa;
  }
  .diff_chg {
    background-color: #ffff77;
  }
  .diff_sub {
    background-color: #ffaaaa;
  }
  .diff_lineno {
    text-align: right;
    background-color: #e0e0e0;
  }
  """
def draw_html(folder1, folder2, result_dir, func1, filter=True):
    comp_folder = folder1 + '_vs_' + folder2
    v1, bin1 = folder1.rsplit('-',1)
    v2, bin2 = folder2.rsplit('-',1)
    # check the matched_functions.txt file, get the matched function of func1
    with open(comp_folder+'/matched_functions.txt','r') as f:
        lines = f.readlines()
    matched_functions = {}
    for l in lines:
        l = l.strip().split()
        matched_functions[l[0]] = l[1]

    if func1 in matched_functions:
        func2 = matched_functions[func1]
    else:
        return
   
    for result in os.listdir(result_dir):
        try:
            if filter:
                suffix = "-match_result.txt"
            else:
                suffix = "-Initial_match_result.txt"
            if result.endswith(suffix):
                srclines1, nodefeatures1, func_features1 = load_nodelabel(os.path.join(comp_folder, v1 + "_" + bin1 + "_nodelabel.txt"))
                srclines2, nodefeatures2, func_features2 = load_nodelabel(os.path.join(comp_folder, v2 + "_" + bin2 + "_nodelabel.txt"))
                decompiled1 = read_decompiled_lines(os.path.join(folder1, 'decompiled',func1 +'.c'))
                decompiled2 = read_decompiled_lines(os.path.join(folder2, 'decompiled', func2 +'.c'))
                print(os.path.join(folder1, 'decompiled',func1 +'.c'))
                print(os.path.join(folder2, 'decompiled', func2 +'.c'))
                # initialize
                matchset1=defaultdict(list)
                matchset2=defaultdict(list)
                for i, l1 in enumerate(decompiled1):
                    matchset1[i+1] = [1 for k in range(len(l1))]
                for i, l2 in enumerate(decompiled2):
                    matchset2[i+1] = [1 for k in range(len(l2))]

                # don't consider tokens that're not meaningful, so only initialize the meaningful tokens as 0 (unmatched)
                for n1 in func_features1[func1]:
                    toklst1 = nodefeatures1[n1][-2].split('@*@')
                    for i in range(0, len(toklst1), 2):
                        if i + 1 < len(toklst1):
                            line_num, col_num = toklst1[i].split(':')
                            length = len(toklst1[i+1])
                            for ind in range(int(col_num), int(col_num)+length):
                                matchset1[int(line_num)][ind]=0

                for n2 in func_features2[func2]:
                    toklst2 = nodefeatures2[n2][-2].split('@*@')
                    for i in range(0, len(toklst2), 2):
                        if i + 1 < len(toklst2):
                            line_num, col_num = toklst2[i].split(':')
                            length = len(toklst2[i+1])
                            for ind in range(int(col_num), int(col_num)+length):
                                matchset2[int(line_num)][ind]=0

                # read the result file and marked the matched tokens as 1 (matched)
                with open(os.path.join(result_dir, result), "r") as f:
                    match_results = f.readlines()
                    for line in match_results:
                        line = line.strip()
                        n1, n2, gtline1, gtline2, correct, sim = line.split(",")
                        if n1 in func_features1[func1] and n2 in func_features2[func2]:
                            if float(sim) < 0.1:
                                continue
                            toklst1 = nodefeatures1[n1][-2].split('@*@')
                            for i in range(0, len(toklst1), 2):
                                if i + 1 < len(toklst1):
                                    line_num, col_num = toklst1[i].split(':')
                                    length = len(toklst1[i+1])
                                    for ind in range(int(col_num), int(col_num)+length):
                                        matchset1[int(line_num)][ind]=1
                                                                 
                            toklst2 = nodefeatures2[n2][-2].split('@*@')
                            for i in range(0, len(toklst2), 2):
                                if i + 1 < len(toklst2):
                                    line_num, col_num = toklst2[i].split(':')
                                    length = len(toklst2[i+1])
                                    for ind in range(int(col_num), int(col_num)+length):
                                        matchset2[int(line_num)][ind]=1
                                        
                # matchset2[line_num][i] is 0 means there is no match, in ltxt it is delete, rtxt it is add
                rows = []
                
                for i in range(max(len(decompiled1), len(decompiled2))):
                    if i >= len(decompiled1):
                        ltxt = ""
                    else:
                        ltxt = decompiled1[i]
                    if i >= len(decompiled2):
                        rtxt = ""
                    else:
                        rtxt = decompiled2[i]
                    lno = i + 1
                    rno = i + 1

                    newltxt = ''
                    for ind in range(0, len(ltxt)):
                        if ind == 0 and matchset1[lno][ind] == 0:
                            newltxt += "\x00-"
                        elif matchset1[lno][ind-1] == 1 and matchset1[lno][ind] == 0:
                            newltxt += "\x00-"
                        newltxt += ltxt[ind]
                        if ind == len(ltxt)-1 and matchset1[lno][ind] == 0:
                            newltxt += "\x01"
                        elif matchset1[lno][ind] == 0 and matchset1[lno][ind+1] == 1:
                            newltxt += "\x01"
                        

                    newrtxt = ''
                    for ind in range(0, len(rtxt)):
                        if ind == 0 and matchset2[rno][ind] == 0:
                            newrtxt += "\x00+"
                        elif matchset2[rno][ind -1] == 1 and matchset2[rno][ind] == 0:
                            newrtxt += "\x00+"
                        newrtxt += rtxt[ind]
                        if ind == len(rtxt)-1 and matchset2[rno][ind] == 0:
                            newrtxt += "\x01"
                        elif matchset2[rno][ind] == 0 and matchset2[rno][ind+1] == 1:
                            newrtxt += "\x01"
                        
                    newltxt = newltxt.replace(" ", "&nbsp;")
                    newrtxt = newrtxt.replace(" ", "&nbsp;")
                    newltxt = newltxt.replace("<", "&lt;")
                    newltxt = newltxt.replace(">", "&gt;")
                    newrtxt = newrtxt.replace("<", "&lt;")
                    newrtxt = newrtxt.replace(">", "&gt;")
                    row = row_template % (str(lno), newltxt, str(rno), newrtxt)
                    rows.append(row)

                all_the_rows = "\n".join(rows)
                all_the_rows = all_the_rows.replace(
                    "\x00+", '<span class="diff_add">').replace(
                    "\x00-", '<span class="diff_sub">').replace(
                    "\x00^", '<span class="diff_chg">').replace(
                    "\x01", '</span>').replace(
                    "\t", 4 * "&nbsp;")

                res = html_template % {"style": style, "rows": all_the_rows}
                # print(res)
                with open(func1 + '.html', 'w') as f:
                    f.write(res)
                return res
        except:
            print(traceback.format_exc())


if __name__ == "__main__":
    folder1 = 'diffutils-3.4-O2-cmp' # the decompiled code of the first binary is stored here
    folder2 = 'diffutils-3.6-O2-cmp' # the decompiled code of the second binary is stored here
    # the preprocessed token information and matched functions are stored here
    # you can use the first column in matched_functions.txt to get the list of function names
    comp_folder = folder1 + '_vs_' + folder2 
    result_dir = comp_folder + "_Pretrain-results" # the results file are stored here
    func1 = 'FUN_00404460' # the function we select to diff
    draw_html(folder1, folder2, result_dir, func1)

