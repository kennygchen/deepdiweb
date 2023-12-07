import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import ghidra.app.cmd.data.CreateArrayCmd;
import ghidra.app.cmd.data.CreateDataCmd;
import ghidra.app.decompiler.ClangLine;
import ghidra.app.decompiler.ClangNode;
import ghidra.app.decompiler.ClangToken;
import ghidra.app.decompiler.ClangTokenGroup;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.component.DecompilerUtils;
import ghidra.app.script.GhidraScript;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.OptionsService;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.ArrayDataType;
import ghidra.program.model.data.BuiltInDataTypeManager;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeConflictHandler;
import ghidra.program.model.data.Pointer;
import ghidra.program.model.data.ProgramBasedDataTypeManager;
import ghidra.program.model.data.StructureDataType;
import ghidra.program.model.lang.Language;
import ghidra.program.model.lang.Register;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.Parameter;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.PcodeBlockBasic;
import ghidra.program.model.pcode.PcodeOp;
import ghidra.program.model.pcode.Varnode;
import ghidra.program.model.pcode.VarnodeAST;
import ghidra.program.model.symbol.RefType;
import ghidra.program.model.symbol.SourceType;
import ghidra.util.exception.InvalidInputException;

public class IndirectCallTargetResolving extends GhidraScript {
	private int nextId = 0;
	private DecompInterface decomplib;
	Address textBegin;
	Address textEnd;
	private HashMap<Function, Graph> allLocalGraphs = new HashMap<Function, Graph>();
	private HashMap<Function, Graph> allBUGraphs = new HashMap<Function, Graph>();
	private HashMap<Address, Cell> allGlobals = new HashMap<Address, Cell>();
	private ArrayList<HashSet<Function>> sccs = new ArrayList<HashSet<Function>>();
	public static int TOP = Integer.MAX_VALUE;
	public HashSet<Address> indirectCallSiteAddrs = new HashSet<Address>();
	public static String pwd = System.getProperty("user.dir");
	public static Path currentPath = Paths.get(pwd);
	public static String parentPath = currentPath.getParent().toString();
	public static String targetPath = parentPath
			+ "/spec2006x86/O2_out/targets.txt";
	public static String outPath = parentPath
			+ "/spec2006x86/O2_out/solved_copy.txt";
	public static String decompiledPath = parentPath
			+ "/spec2006x86/decompiled/";
	private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
	public JSONArray nodes = new JSONArray();
	public JSONArray edges = new JSONArray();
	HashMap<Function, JSONObject> funcMap = new HashMap<Function, JSONObject>();
	HashMap<DSNode, JSONObject> dsnodeMap = new HashMap<DSNode, JSONObject>();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		// the bytes array is in the reverse order
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			int i = bytes.length - j - 1;
			hexChars[i * 2] = HEX_ARRAY[v >>> 4];
			hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0f];
		}

		return new String(hexChars);
	}

	public JSONObject getFuncJSON(Function f) {
		/*
		 * { "key": "__cxa_finalize_thunk", "attributes": { "label":
		 * "__cxa_finalize_thunk", "modularity_class": 1, "MemoryObject": "null",
		 * "Offset": "null" } }
		 */
		JSONObject funcObj = new JSONObject();
		funcObj.put("key", f.toString());
		JSONObject attr = new JSONObject();
		attr.put("label", f.toString());
		attr.put("modularity_class", "1");
		attr.put("MemoryObject", "null");
		attr.put("Offset", "null");
		funcObj.put("attributes", attr);
		return funcObj;
	}

	public String addDSNodeJSON(DSNode ds, String label) {
		if (dsnodeMap.containsKey(ds))
			return (String) dsnodeMap.get(ds).get("key");
		JSONObject funcObj = new JSONObject();
		funcObj.put("key", label + "@offset0");
		JSONObject attr = new JSONObject();
		attr.put("label", label + "@offset0");
		attr.put("modularity_class", "1");
		attr.put("MemoryObject", label);
		attr.put("Offset", "0");
		funcObj.put("attributes", attr);
		dsnodeMap.put(ds, funcObj);
		nodes.add(funcObj);
		return (String) funcObj.get("key");
	}

	public void callGraphToJSON() {
		JSONObject callgraph = new JSONObject();
		FunctionIterator functionManager = this.currentProgram.getFunctionManager().getFunctions(true);

		for (Function func : functionManager) {
			if (funcMap.get(func) == null) {
				funcMap.put(func, getFuncJSON(func));
				nodes.add(funcMap.get(func));
			}
			for (Function callee : func.getCalledFunctions(monitor)) {
				if (funcMap.get(callee) == null) {
					funcMap.put(callee, getFuncJSON(callee));
					nodes.add(funcMap.get(callee));
				}

				/*
				 * { "key": "0", "source": "_init", "target": "__gmon_start___thunk",
				 * "attributes": { "weight": 1.0 } }
				 */
				JSONObject edgeObj = new JSONObject();
				edgeObj.put("key", String.valueOf(edges.size()));
				edgeObj.put("source", func.getName().toString());
				edgeObj.put("target", callee.getName().toString());
				edges.add(edgeObj);
			}
		}
		functionManager = this.currentProgram.getFunctionManager().getFunctions(true);

		for (Function func : functionManager) {
			Graph g = allBUGraphs.get(func);
			for (int offset : g.getStackObjPtr().keySet()) {
				DSNode obj = g.getStackObjPtr().get(offset);
				HashSet<Function> r = obj.getAllReadFunc();
				HashSet<Function> w = obj.getAllWriteFunc();
				if (r.size() > 1 || w.size() > 1) {
					String key = addDSNodeJSON(obj,
							"MemoryObject_" + func.getName() + "_Stackoffset" + String.valueOf(offset));
					for (Function f : r) {
						JSONObject edgeObj = new JSONObject();
						edgeObj.put("key", String.valueOf(edges.size()));
						edgeObj.put("source", key);
						edgeObj.put("target", f.getName().toString());
						edges.add(edgeObj);
					}

					for (Function f : w) {
						JSONObject edgeObj = new JSONObject();
						edgeObj.put("key", String.valueOf(edges.size()));
						edgeObj.put("source", f.getName().toString());
						edgeObj.put("target", key);
						edges.add(edgeObj);
					}
				}
			}
		}

		for (Address addr : allGlobals.keySet()) {
			Cell c = allGlobals.get(addr);
			DSNode obj = c.getParent();
			if (dsnodeMap.containsKey(obj))
				continue;
			HashSet<Function> r = obj.getAllReadFunc();
			HashSet<Function> w = obj.getAllWriteFunc();
			if (r.size() > 1 || w.size() > 1) {
				String key = addDSNodeJSON(obj, "MemoryObject_Global_" + addr.toString());
				for (Function f : r) {
					JSONObject edgeObj = new JSONObject();
					edgeObj.put("key", String.valueOf(edges.size()));
					edgeObj.put("source", key);
					edgeObj.put("target", f.getName().toString());
					edges.add(edgeObj);
				}
				for (Function f : w) {
					JSONObject edgeObj = new JSONObject();
					edgeObj.put("key", String.valueOf(edges.size()));
					edgeObj.put("source", f.getName().toString());
					edgeObj.put("target", key);
					edges.add(edgeObj);
				}
			}
		}
		callgraph.put("nodes", nodes);
		callgraph.put("links", edges);

		try {
			Files.write(Paths.get(IndirectCallTargetResolving.decompiledPath + currentProgram.getName() + ".json"),
					callgraph.toJSONString().getBytes());
		} catch (IOException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}

	/*
	 * set up the decompiler
	 */
	private DecompInterface setUpDecompiler(Program program) {
		DecompInterface decompInterface = new DecompInterface();
		DecompileOptions options;
		options = new DecompileOptions();
		PluginTool tool = state.getTool();
		if (tool != null) {
			OptionsService service = tool.getService(OptionsService.class);
			if (service != null) {
				ToolOptions opt = service.getOptions("Decompiler");
				options.grabFromToolAndProgram(null, opt, program);
			}
		}
		decompInterface.setOptions(options);
		decompInterface.toggleCCode(true);
		decompInterface.toggleSyntaxTree(true);
		decompInterface.setSimplificationStyle("decompile");

		return decompInterface;
	}

	private DecompileResults decompileFunction(Function f) {
		DecompileResults dRes = null;

		try {
			dRes = decomplib.decompileFunction(f, decomplib.getOptions().getDefaultTimeout(), getMonitor());
			// DecompilerSwitchAnalysisCmd cmd = new DecompilerSwitchAnalysisCmd(dRes);
			// cmd.applyTo(currentProgram);
		} catch (Exception exc) {
			DebugUtil.print("EXCEPTION IN DECOMPILATION!\n");
			exc.printStackTrace();
		}

		return dRes;
	}

	public HashMap<PcodeOp, ArrayList<ClangToken>> mapPcodeOpToClangTokenList(ClangTokenGroup ccode) {
		List<ClangNode> lst = new ArrayList<ClangNode>();
		ccode.flatten(lst);
		ArrayList<ClangLine> lines = DecompilerUtils.toLines(ccode);
		HashMap<PcodeOp, ArrayList<ClangToken>> mapping = new HashMap<PcodeOp, ArrayList<ClangToken>>();

		for (ClangLine l : lines) {
			// println(l.toString());
			for (ClangToken c : l.getAllTokens()) {
				if (c.getPcodeOp() != null) {
					// println("--- " + c.toString() + " " + c.getPcodeOp().toString() + " " +
					// c.getPcodeOp().getSeqnum().toString());
					if (!mapping.containsKey(c.getPcodeOp())) {
						mapping.put(c.getPcodeOp(), new ArrayList<ClangToken>());
					}
					mapping.get(c.getPcodeOp()).add(c);
				}
			}
		}
		return mapping;
	}

	public void export(ClangTokenGroup ccode, Function f) {
		try {
			String name;
			if (f.isThunk())
				name = f.getName() + "_thunk";
			else
				name = f.getName();

			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(IndirectCallTargetResolving.decompiledPath + name + ".c")));
			ArrayList<ClangLine> lines = DecompilerUtils.toLines(ccode);
			for (ClangLine l : lines) {
				out.write(l.toString());
				out.newLine();
			}
			out.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void analyzeLocalFuncs(Function f) {
		DecompileResults dRes = decompileFunction(f);
		HighFunction hfunction = dRes.getHighFunction();
		if (hfunction == null)
			return;

		Graph localGraph = new Graph();
		allLocalGraphs.put(f, localGraph);
		localGraph.setF(f);
		localGraph.setAllGlobals(allGlobals);
		localGraph.setAllLocalGraphs(allLocalGraphs);

		ClangTokenGroup ccode = dRes.getCCodeMarkup();
		HashMap<PcodeOp, ArrayList<ClangToken>> mapping = mapPcodeOpToClangTokenList(ccode);
		localGraph.setMapping(mapping);
		export(ccode, f);

		ArrayList<PcodeBlockBasic> bb = hfunction.getBasicBlocks();

		if (bb.size() == 0)
			return;

		Boolean[] visited = new Boolean[bb.size()];
		Arrays.fill(visited, Boolean.FALSE);

		Queue<PcodeBlockBasic> workList = new LinkedList<>();
		workList.addAll(bb);
		for (int i = 0; i < hfunction.getFunctionPrototype().getNumParams(); i++) {
			if (hfunction.getFunctionPrototype().getParam(i).getHighVariable() == null)
				continue;
			Varnode key = hfunction.getFunctionPrototype().getParam(i).getHighVariable().getRepresentative();
			localGraph.addArg(key, "ARG" + String.valueOf(i + 1));
		}
		// for (Variable v : f.getStackFrame().getStackVariables()) {
		// for (Varnode varnode : v.getVariableStorage().getVarnodes())
		// System.out.println("f: " + v.getName() + ", " + toString(varnode,
		// currentProgram.getLanguage()));
		// }
		// Iterator<HighSymbol> symiter = hfunction.getLocalSymbolMap().getSymbols();
		// while (symiter.hasNext()) {
		// HighSymbol sym = symiter.next();
		// if (sym.getHighVariable() == null)
		// continue;
		// for (Varnode varnode : sym.getHighVariable().getInstances()) {
		// System.out.println("high f: " + sym.getName() + ", " + toString(varnode,
		// currentProgram.getLanguage()));
		// }
		// }

		int it = 0;
		while (!workList.isEmpty() && !monitor.isCancelled()) {
			boolean stateChanged = false;
			PcodeBlockBasic pBB = workList.remove();
			// System.out.println("start at " + pBB.getStart() + " end at " +
			// pBB.getStop());
			Iterator<PcodeOp> opIter = pBB.getIterator();
			it++;
			if (it / bb.size() > 4) {
				// this is for debugging
				DebugUtil.print("dead loop!!!");
				break;
			} else if (it / bb.size() > 4) {
				// this is for debugging
				DebugUtil.print("dead loop!!!");
				break;
			}

			// println(pBB.toString());

			while (opIter.hasNext()) {
				PcodeOp pcodeOp = opIter.next();
				boolean changed = analyzePcodeOp(pcodeOp, localGraph);
				stateChanged = stateChanged || changed;
			}

			if (stateChanged) {
				int neighbours = pBB.getOutSize();
				for (int i = 0; i < neighbours; i++) {
					if (!workList.contains(pBB.getOut(i)))
						workList.add((PcodeBlockBasic) pBB.getOut(i));
				}
			}
		}
		localGraph.setMapping(null);

		DebugUtil.print("Used memory "
				+ String.valueOf(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
		DebugUtil.print("Free memory" + String.valueOf(Runtime.getRuntime().freeMemory()));
		DebugUtil.print("node size: " + localGraph.getEv().size());

		// if (!f.getName().toString().contains("select_file_type"))
		// localGraph.getReturnCell();
	}

	public int parseInt(String symbol) {
		int ret;
		if (symbol == "VZERO") {
			ret = 0;
		} else if (symbol.startsWith("0x")) {
			ret = new BigInteger(symbol.substring(2), 16).intValue();
		} else {
			ret = new BigInteger(symbol, 10).intValue();
		}

		return ret;
	}

	public Address getPossiblePointer(PcodeOp pcodeOp) {
		PcodeOp def = pcodeOp.getInput(2).getDef();
		if (def != null && def.getOpcode() == PcodeOp.PTRSUB) {
			Address offset = def.getInput(1).getAddress();

			// fp = this.currentProgram.getFunctionManager().getFunctionAt(offset);
			if (offset.isMemoryAddress())
				return offset;
			Address memAddr = currentProgram.getAddressFactory().getAddress(
					currentProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(), offset.getOffset());
			if (memAddr == null)
				return null;
			Instruction instr = currentProgram.getListing().getInstructionAt(memAddr);
			if (instr != null)
				return memAddr;
		}
		return null;
	}

	public static HashSet<Address> getPossibleFuncPointer(HashSet<Address> allpointers, Program curProgram) {
		HashSet<Address> fps = new HashSet<Address>();
		for (Address p : allpointers) {
			Address memAddr = isFuncPointer(p, curProgram);
			if (memAddr != null)
				fps.add(memAddr);
		}
		return fps;
	}

	public static Address isFuncPointer(Address p, Program curProgram) {
		Address memAddr = curProgram.getAddressFactory()
				.getAddress(curProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(), p.getOffset());
		Function fp = curProgram.getFunctionManager().getFunctionAt(memAddr);
		if (fp != null)
			return memAddr;
		return null;
	}

	/**
	 * load the value from maddr (only when it is not loaded before), and add it to
	 * allGlobal it will be called recursively since the loaded value could points
	 * to another data section it returns true if the current call loads some
	 * function pointers, false if it does not we will not add the corresponding
	 * field if it does not load any function pointers
	 * 
	 * @param maddr
	 * @param mem
	 * @param curProgram
	 * @param allGlobal
	 * @param graph
	 * @return
	 */
	public static boolean loadGlobalVariable(Address maddr, Cell mem, Program curProgram,
			HashMap<Address, Cell> allGlobal, Graph graph, HashSet<DSNode> visited) {
		try {
			if (curProgram.getName().contains("nginx") && maddr.toString().contains("000cce80")) {
				DataType dt = curProgram.getDataTypeManager().getDataType("/DWARF/ngx_core.h/ngx_module_t *");
				CreateArrayCmd cmd = new CreateArrayCmd(maddr, 47, dt, curProgram.getDefaultPointerSize());
				cmd.applyTo(curProgram);
			}

			Data data = curProgram.getListing().getDataAt(maddr);

			if (data == null)
				return false;
			MemoryBlock mb = curProgram.getMemory().getBlock(".text");
			Address textBegin = mb.getStart();
			Address textEnd = mb.getEnd();

			if (data.getBytes().length > 4) {
				byte[] memcont = data.getBytes();
				boolean ret = false;
				for (int i = 0; i < memcont.length && i + 3 < memcont.length; i += 4) {
					byte[] submemcont = new byte[] { memcont[i], memcont[i + 1], memcont[i + 2], memcont[i + 3] };
					String hex = bytesToHex(submemcont);
					Address newAddr = curProgram.getAddressFactory().getAddress(
							curProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
							new BigInteger(hex, 16).longValue());
					if (newAddr == null)
						continue;
					Instruction instr = curProgram.getListing().getInstructionAt(newAddr);

					boolean isText;
					if (newAddr.getOffset() <= textEnd.getOffset() && newAddr.getOffset() >= textBegin.getOffset())
						isText = true;
					else
						isText = false;
					// if the stored pointer points to an valid instruction or the stored pointer
					// points to the data section
					if (instr != null) {
						// if (newAddr.toString().contains("186000"))
						// continue;
						Address curMAddr = maddr.add(i);
						Cell out = null;
						Cell memwithoffset = null;
						if (mem != null) {
							memwithoffset = mem.getParent().getOrCreateCell(mem.getFieldOffset() + i);
							out = memwithoffset.getOutEdges();
						}
						if (out == null) {
							out = new Cell(new DSNode(curMAddr, graph), 0);
							if (memwithoffset != null)
								memwithoffset.setOutEdges(out);
						}
						allGlobal.put(curMAddr, out);
						out.getGlobalAddrs().add(curMAddr);
						out.getParent().setGlobal(true, curMAddr);
						out.addPointers(newAddr);
						if (i == 0 && mem != null) {
							out.setInMemEdges(mem);
							mem.setOutMemEdge(out);
						}
						ret |= true;
					} else if (curProgram.getListing().getDataAt(newAddr) != null && !isText) {
						Address curMAddr = maddr.add(i);
						Cell out = null;
						Cell memwithoffset = null;
						if (mem != null) {
							memwithoffset = mem.getParent().getOrCreateCell(mem.getFieldOffset() + i);
							out = memwithoffset.getOutEdges();
						}
						if (out == null || out.getParent() == null) {
							out = new Cell(new DSNode(curMAddr, graph), 0);
							if (memwithoffset != null)
								memwithoffset.setOutEdges(out);
						}
						allGlobal.put(curMAddr, out);
						out.getGlobalAddrs().add(curMAddr);
						out.getParent().setGlobal(true, curMAddr);
						if (i == 0 && mem != null) {
							out.setInMemEdges(mem);
							mem.setOutMemEdge(out);
						}

						if (curProgram.getName().contains("nginx") && maddr.toString().contains("000cce80")) {
							DataType dt = curProgram.getDataTypeManager().getDataType("/DWARF/ngx_core.h/ngx_module_t");
							Data newdata = curProgram.getListing().getDataAt(newAddr);
							if (newdata != null && newdata.getDataType().toString().startsWith("undefined")) {
								CreateDataCmd cmd2 = new CreateDataCmd(newAddr, true, dt);
								cmd2.applyTo(curProgram);
							}
							newdata = curProgram.getListing().getDataAt(newAddr);
							byte[] membytes = newdata.getBytes();

							// command
							int offsetcmd = 32;
							byte[] submemcmd = new byte[] { membytes[offsetcmd], membytes[offsetcmd + 1],
									membytes[offsetcmd + 2], membytes[offsetcmd + 3] };
							String hexcmd = bytesToHex(submemcmd);
							Address addrCmd = curProgram.getAddressFactory().getAddress(
									curProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
									new BigInteger(hexcmd, 16).longValue());
							newdata = curProgram.getListing().getDataAt(addrCmd);
							if (newdata != null)
								System.out.printf("before offset %d command, size %d\n", i, newdata.getBytes().length);
							if (newdata != null && newdata.getDataType().toString().startsWith("undefined")
									&& i <= 16) {
								DataType dtcmd = curProgram.getDataTypeManager()
										.getDataType("/DWARF/ngx_core.h/ngx_command_t");
								CreateArrayCmd cmd;
								if (i == 0)
									cmd = new CreateArrayCmd(addrCmd, 15, dtcmd, curProgram.getDefaultPointerSize());
								else if (i == 16)
									cmd = new CreateArrayCmd(addrCmd, 6, dtcmd, curProgram.getDefaultPointerSize());
								else
									cmd = new CreateArrayCmd(addrCmd, 1, dtcmd, curProgram.getDefaultPointerSize());
								cmd.applyTo(curProgram);
								newdata = curProgram.getListing().getDataAt(addrCmd);
								System.out.printf("after offset %d command, size %d\n", i, newdata.getBytes().length);
							}

							// context
							offsetcmd = 28;
							submemcmd = new byte[] { membytes[offsetcmd], membytes[offsetcmd + 1],
									membytes[offsetcmd + 2], membytes[offsetcmd + 3] };
							hexcmd = bytesToHex(submemcmd);
							addrCmd = curProgram.getAddressFactory().getAddress(
									curProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
									new BigInteger(hexcmd, 16).longValue());
							newdata = curProgram.getListing().getDataAt(addrCmd);
							if (newdata != null)
								System.out.printf("before offset %d context, size %d\n", i, newdata.getBytes().length);
							if (newdata != null && (newdata.getDataType().toString().startsWith("undefined")
									|| newdata.getDataType().toString().startsWith("pointer")) && i <= 16) {
								DataType dtcmd;
								if (i <= 12)
									dtcmd = curProgram.getDataTypeManager()
											.getDataType("/DWARF/ngx_module.h/ngx_core_module_t");
								else
									dtcmd = curProgram.getDataTypeManager()
											.getDataType("/DWARF/ngx_event.h/ngx_event_module_t");
								CreateDataCmd cmd = new CreateDataCmd(addrCmd, true, dtcmd);
								cmd.applyTo(curProgram);
								newdata = curProgram.getListing().getDataAt(addrCmd);
								if (newdata != null)
									System.out.printf("after offset %d context, size %d\n", i,
											newdata.getBytes().length);
							}

						}
						boolean loadfuncptrs = out.addPointersWithLoading(newAddr, visited);
						// if (!loadfuncptrs) {
						// // newAddr is not a function ptr, the data section it points to also stores
						// no
						// // function ptr, thus we delete it
						// if (mem != null) {
						// mem.getParent().removeCell(memwithoffset.getFieldOffset());
						// }
						// out.removeOutEdges();
						// out.getPossiblePointers().remove(newAddr);
						// }
						ret |= loadfuncptrs;
					} else if (i == 0 && !isText) {
						Address curMAddr = maddr.add(i);
						Cell out = null;
						Cell memwithoffset = null;
						if (mem != null) {
							memwithoffset = mem.getParent().getOrCreateCell(mem.getFieldOffset() + i);
							out = memwithoffset.getOutEdges();
						}
						if (out == null) {
							out = new Cell(new DSNode(curMAddr, graph), 0);
							if (memwithoffset != null)
								memwithoffset.setOutEdges(out);
						}
						allGlobal.put(curMAddr, out);
						out.getGlobalAddrs().add(curMAddr);
						out.getParent().setGlobal(true, curMAddr);
						if (mem != null) {
							out.setInMemEdges(mem);
							mem.setOutMemEdge(out);
						}
					}
				}
				return true;
			}

			byte[] memcont = data.getBytes();
			String hex = bytesToHex(memcont);
			Address newAddr = curProgram.getAddressFactory().getAddress(
					curProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
					new BigInteger(hex, 16).longValue());
			if (newAddr == null)
				return false;
			Instruction instr = curProgram.getListing().getInstructionAt(newAddr);
			boolean isText;
			if (newAddr.getOffset() <= textEnd.getOffset() && newAddr.getOffset() >= textBegin.getOffset())
				isText = true;
			else
				isText = false;
			if (instr != null) {
				Cell out = null;
				if (mem != null)
					out = mem.getOutEdges();
				if (out == null) {
					out = new Cell(new DSNode(maddr, graph), 0);
					if (mem != null)
						mem.setOutEdges(out);
				}
				allGlobal.put(maddr, out);
				out.getGlobalAddrs().add(maddr);
				out.getParent().setGlobal(true, maddr);
				out.addPointers(newAddr);
				if (mem != null) {
					out.setInMemEdges(mem);
					mem.setOutMemEdge(out);
				}
				return true;
			}
			if (isText)
				return false;
			Cell out = null;
			if (mem != null) {
				out = mem.getOutEdges();
			}
			if (out == null || out.getParent() == null) {
				out = new Cell(new DSNode(maddr, graph), 0);
				if (mem != null)
					mem.setOutEdges(out);
			}
			allGlobal.put(maddr, out);
			out.getGlobalAddrs().add(maddr);
			out.getParent().setGlobal(true, maddr);
			if (curProgram.getListing().getDataAt(newAddr) != null) {
				if (mem != null) {
					out.setInMemEdges(mem);
					mem.setOutMemEdge(out);
				}
				boolean loadfuncptrs = out.addPointersWithLoading(newAddr, visited);
			}

			return true;

		} catch (MemoryAccessException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		}
		return false;
	}

	public boolean analyzePcodeOp(PcodeOp pcodeOp, Graph graph) {
		String outStr = toString(pcodeOp, currentProgram.getLanguage()) + " " + pcodeOp.getSeqnum().toString();
		DebugUtil.print(outStr);
		graph.changed = false;
		switch (pcodeOp.getOpcode()) {
			case PcodeOp.UNIMPLEMENTED:
				break;
			case PcodeOp.CAST:
			case PcodeOp.INDIRECT:
				// input0 is copied to output, but the value may be altered in an indirect way
				// by the operation referred to by input1
				// so need to view it as copy. And the stack value need to be updated if shown
				// in the output
				if (pcodeOp.getOutput() == pcodeOp.getInput(0))
					break;
			case PcodeOp.INT_ZEXT:
			case PcodeOp.INT_SEXT:
			case PcodeOp.COPY:
				Pair<Varnode, Integer> newVarRelation = new Pair<Varnode, Integer>(pcodeOp.getInput(0), 0);
				graph.setRelation(pcodeOp.getOutput(), newVarRelation);
				Cell c1 = graph.getCell(pcodeOp.getInput(0));
				Cell c2 = graph.getCell(pcodeOp.getOutput());
				// if (c2 != null && c2.getParent() != null && c2.getParent().isGlobal())
				// c2.merge(c1);
				break;
			case PcodeOp.LOAD:
				Cell mem = graph.getCell(pcodeOp.getInput(1));
				Cell out = graph.getCell(pcodeOp.getOutput());
				mem.addOutEdges(out);
				mem = graph.getCell(pcodeOp.getInput(1));
				mem.setReadFunc(graph.getF());
				if (mem.getPossiblePointersWithLoading(null).size() > 0) {
					// TODO: load the address stored in memory
					HashSet<Address> alladdrs = new HashSet<Address>();
					alladdrs.addAll(mem.getPossiblePointers());
					for (Address maddr : alladdrs) {
						if (isFuncPointer(maddr, currentProgram) != null)
							continue;
						out = mem.getOutEdges();
						// out.getParent().setGlobal(true, maddr);
						// the value stored in address maddr is out, put it into the memory
						if (!allGlobals.containsKey(maddr)) {
							loadGlobalVariable(maddr, mem, currentProgram, allGlobals, graph, null);
						} else {
							// Cell origin = allGlobals.get(maddr);
							// origin.merge(out);
							mem.loadGlobalVarsToThisPtr(maddr, null);
						}

					}
				}
				break;
			case PcodeOp.STORE:
				Address ptr = getPossiblePointer(pcodeOp);
				mem = graph.getCell(pcodeOp.getInput(1));
				mem.setWriteFunc(graph.getF());
				Cell val = graph.getCell(pcodeOp.getInput(2));

				// if the value stored into the memory is a pointer
				if (ptr != null && !val.getPossiblePointersWithLoading(null).contains(ptr)) {
					val.addPointersWithLoading(ptr, null);
					graph.changed = true;
					// only merge with global variable if it stores a pointer
					for (Address maddr : mem.getPossiblePointersWithLoading(null)) {
						if (isFuncPointer(maddr, currentProgram) != null)
							continue;
						val = mem.getOutEdges();
						if (!allGlobals.containsKey(maddr)) {
							allGlobals.put(maddr, val);
							val.getGlobalAddrs().add(maddr);
						} else {
							Cell origin = allGlobals.get(maddr);
							origin.merge(val);
						}
						mem.getOutEdges().getParent().setGlobal(true, maddr);
					}
				}

				mem.addOutEdges(val);

				break;
			case PcodeOp.BRANCH:
				break;
			case PcodeOp.CBRANCH:
				// String cond = mstate.getVnodeValue(pcodeOp.getInput(1), true).toString();
				// mstate.addConditions(pcodeOp.getSeqnum(), cond);
				break;
			case PcodeOp.BRANCHIND:
				break;
			case PcodeOp.CALL:
				Address addr = pcodeOp.getInput(0).getAddress();
				Function fp = this.currentProgram.getFunctionManager().getFunctionAt(addr);
				if (fp != null && (fp.getName().equals("malloc") || fp.getName().equals("calloc"))
						&& pcodeOp.getOutput() != null) {
					out = graph.getCell(pcodeOp.getOutput());
					if (pcodeOp.getInput(1) != null && pcodeOp.getInput(1).isConstant()) {
						out.getParent().setSize((int) pcodeOp.getInput(1).getOffset());
					}
					out.getParent().setOnHeap(true);
					break;
				}
				if (fp != null && fp.getName().contains("memcpy") && pcodeOp.getInputs().length >= 3) {
					c1 = graph.getCell(pcodeOp.getInput(1));
					c2 = graph.getCell(pcodeOp.getInput(2));
					c1.merge(c2);
					break;
				}
				ArrayList<Cell> callargs = new ArrayList<Cell>();
				for (int i = 1; i < pcodeOp.getNumInputs(); ++i) {
					Cell arg = graph.getCell(pcodeOp.getInput(i));
					callargs.add(arg);
				}
				Cell func = graph.getCell(pcodeOp.getInput(0));
				func.addPointers(addr);
				Cell ret = graph.getCell(pcodeOp.getOutput());
				CallSiteNode oldCSite = graph.getCallNodes(pcodeOp.getSeqnum().getTarget());
				if (oldCSite != null) {
					oldCSite.update(ret, func, callargs);
				} else {
					CallSiteNode csite = new CallSiteNode(ret, func, callargs, pcodeOp.getSeqnum().getTarget(), graph);
					graph.addCallNodes(pcodeOp.getSeqnum().getTarget(), csite);
				}
				break;
			case PcodeOp.CALLIND:
				callargs = new ArrayList<Cell>();
				for (int i = 1; i < pcodeOp.getNumInputs(); ++i) {
					Cell arg = graph.getCell(pcodeOp.getInput(i));
					callargs.add(arg);
				}
				func = graph.getCell(pcodeOp.getInput(0));

				// this address could be resolved within current function
				int count = 0;
				HashSet<Address> addrs = new HashSet<Address>();
				addrs.addAll(func.getPossiblePointers());
				for (Address funcAddr : addrs) {
					if (!funcAddr.isMemoryAddress())
						funcAddr = currentProgram.getAddressFactory().getAddress(
								currentProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
								funcAddr.getOffset());

					fp = this.currentProgram.getFunctionManager().getFunctionAt(funcAddr);

					if (fp != null) {
						count += 1;
						Instruction instr = currentProgram.getListing()
								.getInstructionAt(pcodeOp.getSeqnum().getTarget());
						if (instr == null)
							continue;
						instr.addOperandReference(0, funcAddr, RefType.COMPUTED_CALL, SourceType.USER_DEFINED);
					} else {
						func.getPossiblePointers().remove(funcAddr);
					}
				}

				ret = graph.getCell(pcodeOp.getOutput());
				oldCSite = graph.getCallNodes(pcodeOp.getSeqnum().getTarget());
				if (oldCSite != null) {
					oldCSite.update(ret, func, callargs);
				} else {
					CallSiteNode csite = new CallSiteNode(ret, func, callargs, pcodeOp.getSeqnum().getTarget(), graph);
					csite.isIndirect = true;
					csite.numIndirectCall = 1;
					if (pcodeOp.getInput(0).getAddress().isMemoryAddress())
						csite.isGlobalAddr = true;
					graph.addCallNodes(pcodeOp.getSeqnum().getTarget(), csite);
					String tokens = graph.getMapping().get(pcodeOp).get(0).getLineParent().toString();
					csite.setTokens(tokens);
					BufferedWriter outf;
					try {
						outf = new BufferedWriter(
								new OutputStreamWriter(
										new FileOutputStream(IndirectCallTargetResolving.targetPath, true)));
						outf.write(pcodeOp.getSeqnum().getTarget().toString() + "@" + tokens);
						outf.newLine();
						outf.close();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					if (count > 0) {
						try {
							outf = new BufferedWriter(new OutputStreamWriter(
									new FileOutputStream(IndirectCallTargetResolving.outPath, true)));
							outf.write(csite.toString() + "@" + String.valueOf(count) + "@"
									+ func.getPossiblePointers().toString());
							outf.newLine();
							outf.close();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}

				break;
			case PcodeOp.CALLOTHER:
				break;
			case PcodeOp.RETURN:
				graph.getCell(pcodeOp.getInput(1));
				graph.addRet(pcodeOp.getInput(1));
				break;
			case PcodeOp.FLOAT_EQUAL:
			case PcodeOp.FLOAT_NOTEQUAL:
			case PcodeOp.FLOAT_LESS:
			case PcodeOp.FLOAT_LESSEQUAL:
			case PcodeOp.INT_EQUAL:
			case PcodeOp.INT_NOTEQUAL:
			case PcodeOp.INT_SLESS:
			case PcodeOp.INT_SLESSEQUAL:
			case PcodeOp.INT_LESS:
			case PcodeOp.INT_LESSEQUAL:
				break;
			case PcodeOp.PTRSUB:
				if (pcodeOp.getInput(0).isConstant() && pcodeOp.getInput(0).getOffset() == 0) {
					Address offset = pcodeOp.getInput(1).getAddress();
					Address memAddr = currentProgram.getAddressFactory().getAddress(
							currentProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(), offset.getOffset());
					if (memAddr != null && (currentProgram.getListing().getDataAt(memAddr) != null
							|| isFuncPointer(memAddr, currentProgram) != null)) {
						Cell outCell = graph.getCell(pcodeOp.getOutput());
						outCell.addPointersWithLoading(memAddr, null);
						break;
					}
				}
			case PcodeOp.FLOAT_ADD:
			case PcodeOp.INT_CARRY:
			case PcodeOp.INT_SCARRY:
			case PcodeOp.INT_ADD:
				Varnode vnode1 = pcodeOp.getInput(0);
				Varnode vnode2 = pcodeOp.getInput(1);
				Varnode onode = pcodeOp.getOutput();
				Cell cell1 = graph.getCell(vnode1);
				Cell cell2 = graph.getCell(vnode2);

				// if cell1 and cell2 stores normal constants, calculate them
				if (!cell1.getParent().isCollapsed() && !cell2.getParent().isCollapsed()
						&& cell1.getParent().getConstants() != null && cell2.getParent().getConstants() != null) {
					int offset1 = cell1.getParent().getConstants();
					int offset2 = cell2.getParent().getConstants();
					Cell outCell = graph.getCell(onode);
					outCell.getParent().addConstants(offset1 + offset2);
					break;
				}

				if (vnode1.isConstant()) {
					int offset = parseInt(vnode1.toString(currentProgram.getLanguage()));
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode2, offset));
					if (vnode1.getOffset() < currentProgram.getDefaultPointerSize()) {
						Cell outCell = graph.getCell(onode);
						outCell.getParent().setIsConstant(true);
						cell2 = graph.getCell(vnode2);
						cell2.getParent().setIsConstant(true);
					}
				} else if (vnode2.isConstant()) {
					int offset = parseInt(vnode2.toString(currentProgram.getLanguage()));
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, offset));
					if (vnode2.getOffset() < currentProgram.getDefaultPointerSize()) {
						Cell outCell = graph.getCell(onode);
						outCell.getParent().setIsConstant(true);
						cell1 = graph.getCell(vnode1);
						cell1.getParent().setIsConstant(true);
					}
				} else if (cell1.getParent().isCollapsed() && cell1.getParent().getConstants() == null) { // cell1 is
																											// collapsed
																											// but
																											// not
																											// storing
																											// constants
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, 0));
				} else if (cell2.getParent().isCollapsed() && cell2.getParent().getConstants() == null) {
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode2, 0));
				} else if (cell1.getParent().getConstants() != null) {
					int offset = cell1.getParent().getConstants();
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode2, offset));
				} else if (cell2.getParent().getConstants() != null) {
					int offset = cell2.getParent().getConstants();
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, offset));
				}
				break;
			case PcodeOp.FLOAT_SUB:
			case PcodeOp.INT_SBORROW:
			case PcodeOp.INT_SUB:
				vnode1 = pcodeOp.getInput(0);
				vnode2 = pcodeOp.getInput(1);
				onode = pcodeOp.getOutput();
				cell2 = graph.getCell(vnode2);
				cell1 = graph.getCell(vnode1);
				if (cell1.getParent().getConstants() != null && cell2.getParent().getConstants() != null) {
					int offset1 = cell1.getParent().getConstants();
					int offset2 = cell2.getParent().getConstants();
					Cell outCell = graph.getCell(onode);
					outCell.getParent().addConstants(offset1 - offset2);
					break;
				}

				if (cell1.getParent().isCollapsed() && cell1.getParent().getConstants() == null) {
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, 0));
				} else if (cell2.getParent().getConstants() != null) {
					int offset = cell2.getParent().getConstants();
					if (offset == TOP)
						graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, TOP));
					else
						graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, -offset));
					if (offset < currentProgram.getDefaultPointerSize()) {
						Cell outCell = graph.getCell(onode);
						outCell.getParent().setIsConstant(true);
						cell1.getParent().setIsConstant(true);
					}
					graph.getCell(vnode1);
				}
				break;
			case PcodeOp.PIECE:
			case PcodeOp.BOOL_AND:
			case PcodeOp.BOOL_OR:
				vnode1 = pcodeOp.getInput(0);
				vnode2 = pcodeOp.getInput(1);
				onode = pcodeOp.getOutput();
				cell1 = graph.getCell(vnode1);
				cell1.getParent().setIsConstant(true);
				cell2 = graph.getCell(vnode2);
				cell2.getParent().setIsConstant(true);
				Cell outCell = graph.getCell(onode);
				outCell.getParent().setIsConstant(true);
				break;
			case PcodeOp.SUBPIECE:
			case PcodeOp.BOOL_NEGATE:
			case PcodeOp.INT_NEGATE:
			case PcodeOp.INT_2COMP:
			case PcodeOp.FLOAT_NEG:
			case PcodeOp.FLOAT_NAN:
			case PcodeOp.FLOAT_ABS:
			case PcodeOp.FLOAT_SQRT:
			case PcodeOp.FLOAT_INT2FLOAT:
			case PcodeOp.FLOAT_FLOAT2FLOAT:
			case PcodeOp.FLOAT_TRUNC:
			case PcodeOp.FLOAT_CEIL:
			case PcodeOp.FLOAT_FLOOR:
			case PcodeOp.FLOAT_ROUND:
				vnode1 = pcodeOp.getInput(0);
				onode = pcodeOp.getOutput();
				cell1 = graph.getCell(vnode1);
				cell1.getParent().setIsConstant(true);
				outCell = graph.getCell(onode);
				outCell.getParent().setIsConstant(true);
				break;
			case PcodeOp.BOOL_XOR:
			case PcodeOp.INT_XOR:
			case PcodeOp.INT_LEFT:
			case PcodeOp.INT_RIGHT:
			case PcodeOp.INT_SRIGHT:
			case PcodeOp.INT_AND:
			case PcodeOp.INT_OR:
			case PcodeOp.INT_MULT:
			case PcodeOp.FLOAT_MULT:
			case PcodeOp.INT_DIV:
			case PcodeOp.INT_SDIV:
			case PcodeOp.FLOAT_DIV:
			case PcodeOp.INT_REM:
			case PcodeOp.INT_SREM:
				vnode1 = pcodeOp.getInput(0);
				vnode2 = pcodeOp.getInput(1);
				onode = pcodeOp.getOutput();
				cell1 = graph.getCell(vnode1);
				cell2 = graph.getCell(vnode2);
				cell1.getParent().setIsConstant(true);
				cell2.getParent().setIsConstant(true);

				// if cell1 and cell2 stores normal constants, calculate them
				if (cell1.getParent().getConstants() != null && cell2.getParent().getConstants() != null) {
					int offset1 = cell1.getParent().getConstants();
					int offset2 = cell2.getParent().getConstants();
					outCell = graph.getCell(onode);
					outCell.getParent().setIsConstant(true);
					if (cell1.getParent().isCollapsed() || cell2.getParent().isCollapsed())
						outCell.getParent().collapse(true);
					else
						outCell.getParent().addConstants(calculate(offset1, offset2, pcodeOp.getOpcode()));
				} else {
					outCell = graph.getCell(onode);
					outCell.getParent().setIsConstant(true);
				}
				break;
			case PcodeOp.MULTIEQUAL:
				// System.out.println(pcodeOp.toString());
				// TODO: handle changes
				HashSet<DSNode> existNodes = new HashSet<DSNode>();
				boolean isCollapse = false;
				out = graph.getCell(pcodeOp.getOutput());
				existNodes.add(out.getParent());
				for (int i = 0; i < pcodeOp.getNumInputs(); i++) {
					if (graph.getEv(pcodeOp.getInput(i)) == null) {
						out.isLoopVariant = true;
						continue;
					}
					Cell input = graph.getCell(pcodeOp.getInput(i));
					if (existNodes.contains(input.getParent()))
						isCollapse = true;
					existNodes.add(input.getParent());
				}
				for (int i = 0; i < pcodeOp.getNumInputs(); i++) {
					out = graph.getCell(pcodeOp.getOutput());
					if (graph.getEv(pcodeOp.getInput(i)) == null)
						continue;
					Cell input = graph.getCell(pcodeOp.getInput(i));
					if (out != null) {
						if (pcodeOp.getOutput().getAddress().isMemoryAddress() || (isCollapse && out.isLoopVariant))
							out.merge(input);
						// else
						// out.setSubTypeRelation(input);
					}
				}
				break;
			case PcodeOp.PTRADD:
				vnode1 = pcodeOp.getInput(0);
				vnode2 = pcodeOp.getInput(1);
				Varnode vnode3 = pcodeOp.getInput(2);
				onode = pcodeOp.getOutput();
				cell2 = graph.getCell(vnode2);
				Cell cell3 = graph.getCell(vnode3);
				cell1 = graph.getCell(vnode1);
				cell2.getParent().setIsConstant(true);
				cell3.getParent().setIsConstant(true);
				if (cell2.getParent().getConstants() != null && cell3.getParent().getConstants() != null) {
					// do not set collapse here because ghidra may make mistakes identifying arrays
					int offset = cell2.getParent().getConstants();
					int offset2 = cell3.getParent().getConstants();
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, offset * offset2));
					graph.getCell(vnode1);
				} else {
					cell1.getParent().collapse(false);
					graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, TOP));
				}
				// cell1 = graph.getCell(vnode1);
				// cell1.getParent().collapse();
				// graph.setRelation(onode, new Pair<Varnode, Integer>(vnode1, TOP));
				if (cell3.getParent().getConstants() != null) {
					int offset2 = cell3.getParent().getConstants();
					graph.getCell(vnode1).getParent().setPossibleStride(offset2);
				}
				break;
			case PcodeOp.CPOOLREF:
			case PcodeOp.NEW:
			default:

		}
		if (graph.changed) {
			// DebugUtil.print("graph changed");
			return true;
		}
		return false;
	}

	private int calculate(int offset1, int offset2, int opcode) {
		switch (opcode) {
			case PcodeOp.BOOL_XOR:
			case PcodeOp.INT_XOR:
				return offset1 ^ offset2;
			case PcodeOp.INT_LEFT:
				return offset1 << offset2;
			case PcodeOp.INT_RIGHT:
			case PcodeOp.INT_SRIGHT:
				return offset1 >> offset2;
			case PcodeOp.INT_AND:
				return offset1 & offset2;
			case PcodeOp.INT_OR:
				return offset1 | offset2;
			case PcodeOp.INT_MULT:
			case PcodeOp.FLOAT_MULT:
				return offset1 * offset2;
			case PcodeOp.INT_DIV:
			case PcodeOp.INT_SDIV:
			case PcodeOp.FLOAT_DIV:
				if (offset2 != 0)
					return offset1 / offset2;
				return 0;
			case PcodeOp.INT_REM:
			case PcodeOp.INT_SREM:
				if (offset2 != 0)
					return offset1 % offset2;
		}
		return 0;
	}

	public void resolveCallee(Graph caller, Graph callee, CallSiteNode cs) {
		Map<DSNode, DSNode> isomorphism = new IdentityHashMap<DSNode, DSNode>();
		caller.cloneGraphIntoThis(callee, cs, isomorphism);
		Address loc = cs.getLoc();
		// caller.getCallNodes().remove(loc);
		// cs.setResolved(true);
	}

	public void resolveCaller(Graph caller, Graph callee, CallSiteNode cs) {
		Function calleef = callee.getF();
		Map<DSNode, DSNode> isomorphism = new IdentityHashMap<DSNode, DSNode>();
		ArrayList<Cell> argFormal = callee.getArgCell();
		for (int i = 0; i < argFormal.size(); i++) {
			Cell arg = argFormal.get(i);
			if (arg == null)
				continue;
			DSNode argNode = arg.getParent();
			int offset = arg.getFieldOffset();

			setDataType(cs.getArgI(i), calleef.getParameter(i));

		}

		// copy and merge return node
		Cell retCell = callee.getReturnCell();
		DSNode retNode = retCell.getParent();
		int offset = retCell.getFieldOffset();
		if (retNode != null && (retNode.getOnHeap() || retNode.getIsArg() || retNode.getMembers().size() > 1)) {
			setDataType(cs.getReturn(), calleef.getReturn());
		}

	}

	public void setDataType(Cell c, Parameter p) {
		Pointer dtype = c.getParent().getDataType(currentProgram);
		try {
			p.setDataType(dtype, SourceType.USER_DEFINED);
		} catch (InvalidInputException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void processSCC(HashSet<Function> scc, ArrayList<Function> stk, HashMap<Function, Integer> val,
			HashMap<Function, Integer> low, HashMap<Function, Boolean> inStack) {
		boolean newResolvedCallsite = false;
		int count = 0;
		for (int id : val.values()) {
			if (id != -1)
				count += 1;
		}
		System.out.printf("bottom up: %.2f\n", (float) count / val.size());
		System.out.println("Process SCC: " + scc.toString());
		// if (level > 1)
		// return;
		// if (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() >
		// 2000000000)
		// System.gc();

		int sccsLen = sccs.size();
		if (sccsLen > 0 && scc.containsAll(sccs.get(sccsLen - 1)))
			sccs.add(sccsLen - 1, scc);
		else
			sccs.add(scc);

		// merged the callees of all functions in scc
		for (Function f : scc) {
			Graph g = allBUGraphs.get(f);
			if (g == null)
				continue;
			HashSet<CallSiteNode> clonedcs = new HashSet<CallSiteNode>();
			clonedcs.addAll(g.getCallNodes().values());
			for (CallSiteNode cs : clonedcs) {
				if (!cs.getResolved() && cs.getFunc() != null && cs.getFunc().getParent() != null) {
					HashSet<Address> allAddr = new HashSet<Address>();
					allAddr.addAll(cs.getFunc().getPossiblePointers());
					for (Address funcAddr : allAddr) {
						if (!funcAddr.isMemoryAddress())
							funcAddr = currentProgram.getAddressFactory().getAddress(
									currentProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
									funcAddr.getOffset());
						Function fp = this.currentProgram.getFunctionManager().getFunctionAt(funcAddr);
						if (fp != null && !scc.contains(fp) && allBUGraphs.get(fp) != null) {
							resolveCallee(g, allBUGraphs.get(fp), cs);
						}
					}
					if (allAddr.size() > 0)
						cs.setResolved(true);
				}
			}
			if (g.resolvedNewCallSite) {
				newResolvedCallsite = true;
			}
		}

		// merged into a complete scc graph and merge intra-callsites
		Iterator<Function> sccIter = scc.iterator();
		Graph sccgraph = allBUGraphs.get(sccIter.next());
		if (sccgraph == null)
			return;
		Map<DSNode, DSNode> isomorphism = new IdentityHashMap<DSNode, DSNode>();
		ArrayList<CallSiteNode> allCallSite = new ArrayList<CallSiteNode>();
		for (CallSiteNode cs : sccgraph.getCallNodes().values()) {
			if (!cs.getResolved() && cs.getFunc() != null && cs.getFunc().getParent() != null) {
				allCallSite.add(cs);
			}
		}

		HashSet<Address> visitedCS = new HashSet<Address>();
		while (allCallSite.size() > 0) {
			CallSiteNode cs = allCallSite.remove(0);
			if (visitedCS.contains(cs.getLoc()))
				continue;
			visitedCS.add(cs.getLoc());
			if (cs.getFunc().getParent() != null) {
				HashSet<Address> ptrs = new HashSet<Address>();
				ptrs.addAll(cs.getFunc().getPossiblePointers());
				for (Address funcAddr : ptrs) {
					Function fp = this.currentProgram.getFunctionManager().getFunctionAt(funcAddr);
					if (fp != null && scc.contains(fp)) {
						Graph callee = allBUGraphs.get(fp);
						for (CallSiteNode cs2 : callee.getCallNodes().values()) {
							if (!cs2.getResolved() && cs2.getFunc() != null && cs2.getFunc().getParent() != null) {
								allCallSite.add(cs2);
							}
						}

						sccgraph.cloneGraphIntoThis(callee, cs, isomorphism);
						// sccgraph.getCallNodes().remove(loc);
						cs.setResolved(true);
						if (sccgraph.resolvedNewCallSite) {
							newResolvedCallsite = true;
						}
						allBUGraphs.put(fp, sccgraph);
					}
				}
			}

		}

		// new resolvable call sites
		if (newResolvedCallsite) {
			Function f = null;
			Iterator<Function> iter = scc.iterator();
			while (iter.hasNext()) {
				f = iter.next();
				val.put(f, -1);
				low.put(f, -1);
				if (allBUGraphs.get(f) == null)
					continue;
				allBUGraphs.get(f).resolvedNewCallSite = false;
			}
			if (f != null)
				tarjanVisitNode(f, inStack, low, val, stk);
		}
	}

	public void topDownAnalysis() {
		for (int i = sccs.size() - 1; i >= 0; i--) {
			HashSet<Function> scc = sccs.get(i);
			for (Function f : scc) {
				// resolve all function calls in the SCC
				Graph g = allBUGraphs.get(f);
				if (g == null)
					continue;
				HashSet<CallSiteNode> clonedcs = new HashSet<CallSiteNode>();
				clonedcs.addAll(g.getCallNodes().values());
				for (CallSiteNode cs : clonedcs) {
					if (cs.getResolved() && cs.getFunc() != null && cs.getFunc().getParent() != null) {
						for (Address funcAddr : cs.getFunc().getPossiblePointers()) {
							if (!funcAddr.isMemoryAddress())
								funcAddr = currentProgram.getAddressFactory().getAddress(
										currentProgram.getAddressFactory().getAddressSpace("ram").getSpaceID(),
										funcAddr.getOffset());
							Function fp = this.currentProgram.getFunctionManager().getFunctionAt(funcAddr);
							if (fp != null && !scc.contains(fp)) {
								resolveCaller(g, allBUGraphs.get(fp), cs);
							}
						}
					}
				}
			}
		}

	}

	public void bottomUpAnalysis(ArrayList<Function> functionList) {
		HashMap<Function, Integer> val = new HashMap<Function, Integer>();
		HashMap<Function, Integer> low = new HashMap<Function, Integer>();
		HashMap<Function, Boolean> inStack = new HashMap<Function, Boolean>();
		ArrayList<Function> stack = new ArrayList<Function>();
		for (Function func : functionList) {
			val.put(func, -1);
			low.put(func, -1);
			inStack.put(func, false);
			allBUGraphs.put(func, allLocalGraphs.get(func));
		}

		for (Function func : functionList) {
			if (val.get(func) == -1) {
				tarjanVisitNode(func, inStack, low, val, stack);
			}
		}

	}

	public void tarjanVisitNode(Function f, HashMap<Function, Boolean> inStack, HashMap<Function, Integer> low,
			HashMap<Function, Integer> val, ArrayList<Function> stk) {
		val.put(f, nextId);
		low.put(f, nextId);
		nextId += 1;
		inStack.put(f, true);
		stk.add(f);
		for (Function callee : f.getCalledFunctions(monitor)) {
			if (val.get(callee) == -1) {
				tarjanVisitNode(callee, inStack, low, val, stk);
				low.put(f, low.get(f) < low.get(callee) ? low.get(f) : low.get(callee));
			} else if (inStack.get(callee)) {
				low.put(f, low.get(f) < val.get(callee) ? low.get(f) : val.get(callee));
			}
		}

		if (low.get(f).intValue() == val.get(f).intValue()) {
			HashSet<Function> scc = new HashSet<Function>();
			while (true) {
				Function sccfunc = stk.remove(stk.size() - 1);
				scc.add(sccfunc);
				inStack.put(sccfunc, false);
				if (sccfunc == f)
					break;

			}
			processSCC(scc, stk, val, low, inStack);
		}
	}

	public void run() throws Exception {
		this.decomplib = setUpDecompiler(this.currentProgram);
		if (!this.decomplib.openProgram(this.currentProgram)) {
			DebugUtil.printf("Decompiler error: %s\n", this.decomplib.getLastMessage());
			return;
		}

		BufferedWriter out = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(IndirectCallTargetResolving.outPath)));
		out.close();
		out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(IndirectCallTargetResolving.targetPath)));
		out.close();

		long start = System.currentTimeMillis();
		FunctionIterator functionManager = this.currentProgram.getFunctionManager().getFunctions(true);
		ArrayList<Function> funcList = new ArrayList<Function>();
		for (Function func : functionManager) {
			System.out.printf("Found target function %s @ %s %s, %.2f\n",
					new Object[] { func.getName(), Long.toHexString(func.getEntryPoint().getOffset()),
							this.currentProgram.getName(), (double) (funcList.size()) });
			DebugUtil.print(func.getName());

			// if (!func.getName().toString().contains("intrapred_luma_16x16"))
			// continue;

			// local analysis phase
			analyzeLocalFuncs(func);
			funcList.add(func);
		}
		ProgramBasedDataTypeManager dm = currentProgram.getDataTypeManager();
		bottomUpAnalysis(funcList);
		System.out.printf("runtime: %ds\n", (System.currentTimeMillis() - start) / 1000);
		System.out.printf("memory %dM\n",
				(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));

		// topDownAnalysis();
		callGraphToJSON();

	}

	public String toString(PcodeOp p, Language l) {
		String s;
		if (p.getOutput() != null)
			s = toString(p.getOutput(), l);
		else
			s = " ";
		s += " " + p.getMnemonic() + " ";
		for (int i = 0; i < p.getNumInputs(); i++) {
			if (p.getInput(i) == null) {
				s += "null";
			} else {
				s += toString(p.getInput(i), l);
			}

			if (i < p.getNumInputs() - 1)
				s += " , ";
		}
		// s += " " + p.getSeqnum().toString();
		return s;
	}

	public String toString(Varnode v, Language language) {
		String varName = "";
		if (v.isAddress() || v.isRegister()) {
			Register reg = language.getRegister(v.getAddress(), v.getSize());
			if (reg != null) {
				varName = reg.getName();
				varName += '_' + String.valueOf(v.hashCode());
				return varName;
			}
		}
		if (v.isUnique()) {
			varName = "u_" + Long.toHexString(v.getOffset()) + ":" + v.getSize();
		} else if (v.isConstant()) {
			varName = "0x" + Long.toHexString(v.getOffset());
		} else {
			varName = "A_" + v.getAddress() + ":" + v.getSize();
		}
		varName += '_' + String.valueOf(v.hashCode());
		return varName;
	}

}

class Graph {
	private HashMap<VarnodeAST, Cell> Ev = new HashMap<VarnodeAST, Cell>();
	private HashMap<Varnode, Pair<Varnode, Integer>> varRelation = new HashMap<Varnode, Pair<Varnode, Integer>>();
	private HashMap<Address, CallSiteNode> callNodes = new HashMap<Address, CallSiteNode>();
	private HashMap<Address, HashSet<CallSiteNode>> callNodesTemp = new HashMap<Address, HashSet<CallSiteNode>>();
	private ArrayList<Varnode> args = new ArrayList<Varnode>();
	private HashSet<Varnode> ret = new HashSet<Varnode>();
	public HashMap<Integer, Cell> stackObj = new HashMap<Integer, Cell>();
	private Function f;
	public boolean changed = false;
	public boolean resolvedNewCallSite = false;
	public HashMap<Address, Cell> allGlobal;
	public HashMap<Function, Graph> allLocalGraphs;
	private HashMap<PcodeOp, ArrayList<ClangToken>> mapping;
	private HashMap<String, Cell> calleeargs = new HashMap<String, Cell>();
	private HashMap<Integer, DSNode> stackObjPtr = new HashMap<Integer, DSNode>();

	public HashMap<String, Cell> getCalleeargs() {
		return calleeargs;
	}

	public void setCalleeargs(HashMap<String, Cell> calleeargs) {
		this.calleeargs = calleeargs;
	}

	public void addArg(Varnode v, String argno) {
		Cell c = this.getCell(v);
		c.getParent().setArgNo(argno);
		c.getParent().setIsArg(true);
		args.add(v);
	}

	public HashMap<PcodeOp, ArrayList<ClangToken>> getMapping() {
		return mapping;
	}

	public void setMapping(HashMap<PcodeOp, ArrayList<ClangToken>> m) {
		mapping = m;
	}

	public void addRet(Varnode v) {
		this.getCell(v);
		ret.add(v);
	}

	public Cell getReturnCell() {
		Cell merged = new Cell(null, 0);
		for (Varnode v : ret) {
			Cell c = this.getCell(v);
			if (c == null || c.getParent() == null)
				continue;
			merged = c.merge(merged);
		}
		return merged;
	}

	public ArrayList<Cell> getArgCell() {
		ArrayList<Cell> cells = new ArrayList<Cell>();
		for (Varnode v : args) {
			cells.add(this.getCell(v));
		}
		return cells;
	}

	public Cell getArgCell(int i) {
		Varnode v = args.get(i);
		return this.getCell(v);
	}

	public HashMap<Address, CallSiteNode> getCallNodes() {
		return callNodes;
	}

	public CallSiteNode getCallNodes(Address addr) {
		return callNodes.get(addr);
	}

	// public CallSiteNode getCallNodes(Address addr) {
	// ArrayList<Address> addrlist = new ArrayList<Address>();
	// addrlist.add(addr);
	// return callNodes.get(addrlist.toString());
	// }

	public CallSiteNode addCallNodes(Address addr, CallSiteNode cn) {
		callNodes.put(addr, cn);
		return cn;
	}

	public CallSiteNode addCallNodesToTmp(Address addr, CallSiteNode cn) {
		if (!callNodesTemp.containsKey(addr))
			callNodesTemp.put(addr, new HashSet<CallSiteNode>());
		// callNodesTemp.get(addr).add(cn);
		callNodesTemp.get(addr).add(cn);
		return cn;
	}

	public HashSet<CallSiteNode> getTmpCallNodes(Address addr) {
		return callNodesTemp.get(addr);
	}

	// public void addCallNodes(Address addr, CallSiteNode cn) {
	// ArrayList<Address> addrlist = new ArrayList<Address>();
	// addrlist.add(addr);
	// callNodes.put(addrlist.toString(), cn);
	// }

	public Pair<Varnode, Integer> getRelation(Varnode var) {
		return this.varRelation.get(var);
	}

	public void setRelation(Varnode var, Pair<Varnode, Integer> rel) {
		if (this.varRelation.containsKey(var)) {
			Pair<Varnode, Integer> existingPair = this.varRelation.get(var);
			if (existingPair.getK() == rel.getK() && existingPair.getV().intValue() == rel.getV().intValue())
				return;
			if (existingPair.getK() != rel.getK() || existingPair.getV().intValue() != rel.getV().intValue()) {
				this.changed = true;
				this.varRelation.put(var, rel);
				Varnode varBase = rel.getK();
				int offset = rel.getV();
				if (Ev.containsKey(varBase) && Ev.get(varBase) != null) {
					Cell baseCell = Ev.get(varBase);
					DSNode baseNode = baseCell.getParent();
					int baseField = baseCell.getFieldOffset();
					if (baseNode.isCollapsed() || baseField == IndirectCallTargetResolving.TOP
							|| offset == IndirectCallTargetResolving.TOP) {
						Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(0));
					} else
						Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(offset + baseField));
				}
			}
		}
		this.varRelation.put(var, rel);
		this.changed = true;
		this.getCell(rel.getK());
		this.getCell(var);
	}

	public Cell getOrCreateFromStack(long offset, Address addr) {
		if (stackObj.containsKey((int) offset))
			return stackObj.get((int) offset);

		DSNode nnode = new DSNode(addr, this);
		nnode.setOnStack(true);
		Cell ncell = new Cell(nnode, 0);
		stackObj.put((int) offset, ncell);
		ncell.addStackLocs(f, (int) offset);
		return ncell;
	}

	public Cell getCell(Varnode var) {
		if (var == null)
			return null;

		// for variables such as A_0035fdd8, it directly represents the content of the
		// global variable
		// only merge with the global if stores pointer to it
		Cell globalOrStackCell = null;
		if (var.getAddress().isMemoryAddress()) {
			Cell existing = allGlobal.get(var.getAddress());
			if (existing != null && existing.getParent() != null) {
				globalOrStackCell = existing;
			} else {
				DSNode baseNode = new DSNode(var.getAddress(), this);
				Cell mem = new Cell(baseNode, 0);
				mem.addPointers(var.getAddress());
				if (IndirectCallTargetResolving.loadGlobalVariable(var.getAddress(), mem, this.getF().getProgram(),
						this.allGlobal, this, null) && allGlobal.containsKey(var.getAddress()))
					globalOrStackCell = allGlobal.get(var.getAddress());
				else {
					DSNode nnode = new DSNode(var.getPCAddress(), this);
					Cell ncell = new Cell(nnode, 0);
					allGlobal.put(var.getAddress(), ncell);
					ncell.getGlobalAddrs().add(var.getAddress());
					nnode.setGlobal(true, var.getAddress());
					globalOrStackCell = ncell;
				}
			}
		} else if (var.getAddress().isStackAddress() && var.getAddress().getOffset() < 0) {
			long offset = var.getAddress().getOffset();
			Cell newCell = getOrCreateFromStack(offset, var.getPCAddress());
			globalOrStackCell = newCell;
		}
		if (!varRelation.containsKey(var) && globalOrStackCell != null) {
			Ev.put((VarnodeAST) var, globalOrStackCell);
			globalOrStackCell.addInEvEdges(var);
			return globalOrStackCell;
		}

		if (!Ev.containsKey(var) || Ev.get(var) == null || Ev.get(var).getParent() == null) {
			if (varRelation.containsKey(var)) {
				// get Cell according to the relation with existing nodes
				Pair<Varnode, Integer> vr = varRelation.get(var);
				Varnode varBase = vr.getK();
				int offset = vr.getV();
				Cell baseCell;
				DSNode baseNode;

				String varBaseStr = varBase.toString(this.getF().getProgram().getLanguage());

				if (Ev.containsKey(varBase) && Ev.get(varBase) != null && Ev.get(varBase).getParent() != null) {
					baseCell = Ev.get(varBase);
					baseNode = baseCell.getParent();
					int baseField = baseCell.getFieldOffset();
					// if varBase is RSP, we will create a new DSNode for var, we don't use the
					// DSNode for varBase
					if (varBaseStr.equals("RSP") || varBaseStr.equals("ESP")) {
						// TODO: need to check whether new node is necessary
						DSNode dsNode = new DSNode(var.getPCAddress(), this);
						Cell newCell = new Cell(dsNode, 0);
						if (offset < 0) {
							Cell stackobj = getOrCreateFromStack(offset, var.getPCAddress());
							newCell.setOutEdges(stackobj);
							newCell.setRSPOffset(this.getF(), offset);
						}
						Ev.put((VarnodeAST) var, newCell);
					} else if (baseNode.isCollapsed() || baseField == IndirectCallTargetResolving.TOP
							|| offset == IndirectCallTargetResolving.TOP) {
						// baseNode.collapse();
						Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(0));
					} else
						Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(offset + baseField));

				} else if (allGlobal.containsKey(varBase.getAddress())
						&& allGlobal.get(varBase.getAddress()).getParent() != null) {
					baseCell = allGlobal.get(varBase.getAddress());
					baseNode = baseCell.getParent();
					int baseField = baseCell.getFieldOffset();
					if (baseNode.isCollapsed() || baseField == IndirectCallTargetResolving.TOP
							|| offset == IndirectCallTargetResolving.TOP) {
						// baseNode.collapse();
						Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(0));
					} else
						Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(offset + baseField));
				} else {
					// need to create new node for baseNode
					baseNode = new DSNode(varBase.getPCAddress(), this);
					if (varBase.isConstant())
						baseNode.addConstants((int) varBase.getOffset());
					baseCell = new Cell(baseNode, 0);
					Ev.put((VarnodeAST) varBase, baseCell);

					if (varBaseStr.equals("RSP") || varBaseStr.equals("ESP")) {
						DSNode dsNode = new DSNode(var.getPCAddress(), this);
						Cell newCell = new Cell(dsNode, 0);
						if (offset < 0) {
							Cell stackobj = getOrCreateFromStack(offset, var.getPCAddress());
							newCell.setOutEdges(stackobj);
							newCell.setRSPOffset(this.getF(), offset);
						}
						Ev.put((VarnodeAST) var, newCell);
					}

					else if (offset == IndirectCallTargetResolving.TOP) {
						// baseNode.collapse();
						Ev.put((VarnodeAST) var, baseCell);
					} else
						Ev.put((VarnodeAST) var, new Cell(baseNode, offset));
				}
				if (!baseNode.isCollapsed())
					for (Address ptr : baseCell.getPossiblePointers())
						Ev.get(var).addPointersWithLoading(ptr.add(offset), new HashSet<DSNode>());
			} else {
				// create new nodes on its own
				DSNode dsNode = new DSNode(var.getPCAddress(), this);
				Cell newCell = new Cell(dsNode, 0);
				if (var.isConstant())
					dsNode.addConstants((int) var.getOffset());
				String varStr = var.toString(this.getF().getProgram().getLanguage());
				if (varStr.equals("RSP") || varStr.equals("ESP")) {
					newCell.setRSPOffset(this.getF(), 0);
				}
				Ev.put((VarnodeAST) var, newCell);
			}
			this.changed = true;
			Ev.get(var).addInEvEdges(var);
			if (globalOrStackCell != null)
				globalOrStackCell.merge(Ev.get(var));
			return Ev.get(var);
		}

		Cell oldCell = Ev.get(var);
		if (varRelation.containsKey(var)) {
			// if var is added to Ev before, we need to check whether the value is changed
			// due to the changes in varRelation
			Pair<Varnode, Integer> vr = varRelation.get(var);
			Varnode varBase = vr.getK();
			int offset = vr.getV();
			String varBaseStr = varBase.toString(this.getF().getProgram().getLanguage());
			if (varBaseStr.equals("RSP") || varBaseStr.equals("ESP")) {
				return oldCell;
			}
			if (Ev.containsKey(varBase) && Ev.get(varBase) != null && Ev.get(varBase).getParent() != null) {
				Cell baseCell = Ev.get(varBase);
				DSNode baseNode = baseCell.getParent();
				int baseField = baseCell.getFieldOffset();
				if (baseNode.isCollapsed() || baseField == IndirectCallTargetResolving.TOP
						|| offset == IndirectCallTargetResolving.TOP) {
					// baseNode.collapse();
					Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(0));
				} else
					Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(offset + baseField));
			} else if (allGlobal.containsKey(varBase.getAddress())
					&& allGlobal.get(varBase.getAddress()).getParent() != null) {
				Cell baseCell = allGlobal.get(varBase.getAddress());
				DSNode baseNode = baseCell.getParent();
				int baseField = baseCell.getFieldOffset();
				if (baseNode.isCollapsed() || baseField == IndirectCallTargetResolving.TOP
						|| offset == IndirectCallTargetResolving.TOP) {
					// baseNode.collapse();
					Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(0));
				} else
					Ev.put((VarnodeAST) var, baseNode.getOrCreateCell(offset + baseField));
			} else {
				DSNode baseNode = new DSNode(varBase.getPCAddress(), this);
				if (varBase.isConstant())
					baseNode.addConstants((int) varBase.getOffset());
				Cell baseCell = new Cell(baseNode, 0);

				Ev.put((VarnodeAST) varBase, baseCell);
				if (offset == IndirectCallTargetResolving.TOP) {
					// baseNode.collapse();
					Ev.put((VarnodeAST) var, baseCell);
				} else
					Ev.put((VarnodeAST) var, new Cell(baseNode, offset));
			}
			Ev.get(var).addInEvEdges(var);
			if (globalOrStackCell != null)
				globalOrStackCell.merge(Ev.get(var));
			Cell newCell = Ev.get(var);
			if (newCell != oldCell) {
				this.changed = true;
			}
			return Ev.get(var);
		}

		// Cell targetCell = Ev.get(var);
		// if (targetCell.getParent().isCollapsed())
		// Ev.put(var, targetCell.getParent().get(0));
		Ev.get(var).addInEvEdges(var);
		return Ev.get(var);

	}

	public Cell getEv(Varnode var) {
		return this.Ev.get(var);
	}

	public HashMap<VarnodeAST, Cell> getEv() {
		return this.Ev;
	}

	public void setEv(Varnode var, Cell c) {
		if (c == null) {
			this.Ev.remove(var);
			return;
		}
		c.addInEvEdges(var);
		this.Ev.put((VarnodeAST) var, c);
	}

	public Function getF() {
		return f;
	}

	public void setF(Function f) {
		this.f = f;
	}

	public void setAllGlobals(HashMap<Address, Cell> map) {
		this.allGlobal = map;
	}

	public HashMap<Address, Cell> getAllGlobals() {
		return this.allGlobal;
	}

	public void setAllLocalGraphs(HashMap<Function, Graph> allLocalGraphs) {
		this.allLocalGraphs = allLocalGraphs;
	}

	public HashMap<Function, Graph> getAllLocalGraphs() {
		return this.allLocalGraphs;
	}

	public void cloneGraphIntoThis(Graph callee, CallSiteNode cs, Map<DSNode, DSNode> isomorphism) {
		System.out.println(
				"merge " + callee.getF().toString() + " to " + this.getF().toString() + " @" + cs.getLoc().toString());
		DebugUtil.print(
				"merge " + callee.getF().toString() + " to " + this.getF().toString() + " @" + cs.getLoc().toString());
		ArrayList<Cell> argFormal = callee.getArgCell();

		// check arity and types
		int actualsize = 0;
		if (cs.getMembers().containsKey(0))
			actualsize = -1;
		if (argFormal.size() != actualsize + cs.getMembers().size() - 1) {
			cs.getFunc().possiblePointers.remove(callee.getF().getEntryPoint());
			return;
		}
		for (int i = 0; i < argFormal.size(); i++) {
			Cell formalArgCell = argFormal.get(i);
			Cell actualArgCell = cs.getArgI(i);
			if (cs.isIndirect && hasTypeConflict(formalArgCell, actualArgCell)) {
				cs.getFunc().possiblePointers.remove(callee.getF().getEntryPoint());
				return;
			}
		}

		// copy formal args
		for (int i = 0; i < argFormal.size(); i++) {
			Cell arg = argFormal.get(i);
			if (arg == null)
				continue;
			DSNode argNode = arg.getParent();
			if (argNode == null)
				continue;
			int offset = arg.getFieldOffset();
			DSNode copiedNode = argNode.deepCopy(isomorphism, this, cs);
			Cell formalArgCell = copiedNode.get(offset);
			if (formalArgCell == null)
				continue;
			this.calleeargs.put("CALLEEARG" + String.valueOf(i), formalArgCell);
			formalArgCell.addCalleeArgLabel("CALLEEARG" + String.valueOf(i));
		}

		// copy return cells
		Cell retCell = callee.getReturnCell();
		DSNode retNode = retCell.getParent();
		int offset = retCell.getFieldOffset();
		if (retNode != null && (retNode.getOnHeap() || retNode.getIsArg() || retNode.getMembers().size() > 1)) {
			DSNode copiedRetNode = retNode.deepCopy(isomorphism, this, cs);
			Cell formalArgCell = copiedRetNode.get(offset);
			if (formalArgCell != null) {
				this.calleeargs.put("CALLEERET", formalArgCell);
				formalArgCell.addCalleeArgLabel("CALLEERET");
			}
		}

		// TODO: divide the stack

		// merge args and ret
		HashSet<String> keySet = new HashSet<String>();
		keySet.addAll(this.calleeargs.keySet());
		for (String key : keySet) {
			Cell formalArgCell = this.calleeargs.get(key);
			Cell actualArgCell = null;
			if (key.equals("CALLEERET"))
				actualArgCell = cs.getReturn();
			else {
				String argno = key.substring(9, key.length());
				int i = Integer.valueOf(argno);
				actualArgCell = cs.getArgI(i);
			}
			if (actualArgCell == null)
				continue;
			if (actualArgCell.isRSP(this.f)) {
				int stackoffset = actualArgCell.getRSPOffset(this.f);
				int objsize = formalArgCell.getParent().getSize() - formalArgCell.getParent().getMinOffset();
				this.mergeStackObj(actualArgCell, stackoffset, objsize);
			}
			formalArgCell.merge(actualArgCell);
		}

		for (Address addr : this.callNodesTemp.keySet()) {
			Iterator<CallSiteNode> iter = this.callNodesTemp.get(addr).iterator();
			if (!callNodes.containsKey(addr)) {
				callNodes.put(addr, iter.next());
			}
			CallSiteNode exist = callNodes.get(addr);
			while (iter.hasNext()) {
				CallSiteNode cn = iter.next();
				if (exist.getMembers().size() != cn.getMembers().size()) {
					continue;
				}
				Set<Integer> size = exist.getMembers().keySet();
				int max = Collections.max(size);
				for (int i = 0; i < max; i++) {
					// merge newCell to existCell
					Cell newCell = cn.getMembers().get(i);
					Cell existCell = exist.getMembers().get(i);
					if (newCell != null) {
						HashSet<Pair<CallSiteNode, String>> csNodePairs = new HashSet<Pair<CallSiteNode, String>>();
						csNodePairs.addAll(newCell.getInCallSite());
						// remove the callsitenode in newCell that relates to cn, because we don't want
						// to copy that
						for (Pair<CallSiteNode, String> csNodePair : csNodePairs) {
							if (csNodePair.getK() == cn) {
								String v = csNodePair.getV();
								newCell.getInCallSite().remove(csNodePair);

								if (existCell == null) {
									exist.getMembers().put(i, newCell);
									csNodePair = new Pair<CallSiteNode, String>(exist, v);
									newCell.addInCallSite(csNodePair); // newCell is now in exist
								}
							}
						}
					}
					if (existCell != null)
						existCell.merge(newCell);
				}
				cn.getMembers().clear();
			}
			this.callNodesTemp.get(addr).clear();
		}
		this.callNodesTemp.clear();

		if (callee.resolvedNewCallSite) {
			this.resolvedNewCallSite = true;
			callee.resolvedNewCallSite = false;
		}

		for (String key : this.calleeargs.keySet()) {
			Cell formalArgCell = this.calleeargs.get(key);
			formalArgCell.clearCalleeArgLabel();
		}
		this.calleeargs.clear();
		DebugUtil.print("Used memory "
				+ String.valueOf(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
		DebugUtil.print("Free memory " + String.valueOf(Runtime.getRuntime().freeMemory()));
	}

	private boolean hasTypeConflict(Cell formalArgCell, Cell actualArgCell) {
		int field1 = formalArgCell.getFieldOffset();
		int field2 = actualArgCell.getFieldOffset();
		DSNode parent1 = formalArgCell.getParent();
		DSNode parent2 = actualArgCell.getParent();
		if (parent1 == null || parent2 == null)
			return false;
		if (parent1.getIsConstant() && parent2.hasOut())
			return true;
		else if (parent2.getIsConstant() && parent1.hasOut())
			return true;
		else if (parent1.getIsConstant() && parent2.getIsConstant())
			return false;

		Set<Integer> keyset1 = new HashSet<Integer>(parent1.members.keySet());
		Set<Integer> keyset2 = new HashSet<Integer>(parent2.members.keySet());
		if (keyset1.size() == 0 || keyset2.size() == 0)
			return false;
		if (parent1.isCollapsed() || parent2.isCollapsed()) {
			field1 = Collections.min(keyset1);
			field2 = Collections.min(keyset2);
		}
		int offset = field1 - field2;
		keyset2.clear();
		for (int key : parent2.members.keySet()) {
			keyset2.add(key + offset);
		}
		keyset1.retainAll(keyset2);
		for (int key : keyset1) {
			Boolean type1 = parent1.getMemberType().get(key);
			Boolean type2 = parent2.getMemberType().get(key - offset);
			if (type1 == null || type2 == null)
				continue;
			if (type1 && !type2)
				return true;
			if (!type1 && type2)
				return true;
		}

		return false;
	}

	private void mergeStackObj(Cell actualArgCell, int stackoffset, int objsize) {
		ArrayList<Integer> keyset = new ArrayList<Integer>();
		keyset.addAll(stackObj.keySet());
		Collections.sort(keyset);
		assert (actualArgCell.getOutEdges() == stackObj.get(stackoffset));
		DSNode curObjPtr = actualArgCell.getParent();
		if (curObjPtr == null)
			return;
		int originCellOffset = actualArgCell.getFieldOffset();
		int existSize = curObjPtr.getSize(); // we don't merge if it's been merged before
		for (int i : keyset) {
			if (i > existSize - originCellOffset + stackoffset && i < stackoffset + objsize) {
				Cell newCell = curObjPtr.getOrCreateCell(i - stackoffset + originCellOffset);
				newCell.addOutEdges(stackObj.get(i));
				// stackObj.remove(i);
			}
		}
		this.addStackObjPtr(stackoffset, curObjPtr);
	}

	public HashMap<Integer, DSNode> getStackObjPtr() {
		return stackObjPtr;
	}

	public void addStackObjPtr(int offset, DSNode stackObj) {
		this.stackObjPtr.put(offset, stackObj);
	}

}

class DSNode {
	private int size;
	private String name;
	protected HashMap<Integer, Cell> members;
	private HashMap<Integer, Integer> memberSize;
	private HashMap<Integer, Boolean> memberType; // false: constant, true: pointer, null: not sure
	private HashMap<Cell, HashSet<Cell>> subTypes;// Cell1 >= Cell2
	private HashMap<Cell, HashSet<Cell>> superTypes; // Cell1 <= Cell2
	private boolean isArray;
	private boolean isGlobal;
	private boolean collapsed;
	private int possibleStride;
	private boolean onHeap;
	private boolean onStack;
	private boolean isArg;
	private int minkey;
	protected Address loc;
	protected Graph g;
	private String argNo;
	private HashSet<AllocSite> allocationSites;
	private ArrayList<Address> mergedWith;
	private Integer possibleConstant;
	private Pointer dtype;
	private boolean isConstant;

	public DSNode() {
		this.members = new HashMap<Integer, Cell>();
		this.memberSize = new HashMap<Integer, Integer>();
		this.setMemberType(new HashMap<Integer, Boolean>());
		this.subTypes = new HashMap<Cell, HashSet<Cell>>();
		this.superTypes = new HashMap<Cell, HashSet<Cell>>();
		this.isArray = false;
		this.collapsed = false;
		this.allocationSites = new HashSet<AllocSite>();
		this.mergedWith = new ArrayList<Address>();
		this.possibleConstant = null;
		this.isConstant = false;
		this.onHeap = false;
		this.onStack = false;
		this.isArg = false;
		this.minkey = 0;
	}

	public DSNode(Address loc, Graph g) {
		this.members = new HashMap<Integer, Cell>();
		this.memberSize = new HashMap<Integer, Integer>();
		this.setMemberType(new HashMap<Integer, Boolean>());
		this.subTypes = new HashMap<Cell, HashSet<Cell>>();
		this.isArray = false;
		this.collapsed = false;
		this.allocationSites = new HashSet<AllocSite>();
		this.mergedWith = new ArrayList<Address>();
		this.mergedWith.add(loc);
		this.possibleConstant = null;
		this.isConstant = false;
		this.onHeap = false;
		this.onStack = false;
		this.isArg = false;
		this.loc = loc;
		this.g = g;
		this.minkey = 0;
	}

	public DSNode(int size, Address loc, Graph g) {
		this.size = size;
		this.members = new HashMap<Integer, Cell>();
		this.memberSize = new HashMap<Integer, Integer>();
		this.setMemberType(new HashMap<Integer, Boolean>());
		this.subTypes = new HashMap<Cell, HashSet<Cell>>();
		this.superTypes = new HashMap<Cell, HashSet<Cell>>();
		this.isArray = false;
		this.collapsed = false;
		this.allocationSites = new HashSet<AllocSite>();
		this.mergedWith = new ArrayList<Address>();
		this.mergedWith.add(loc);
		this.possibleConstant = null;
		this.isConstant = false;
		this.onHeap = false;
		this.onStack = false;
		this.isArg = false;
		this.loc = loc;
		this.g = g;
		this.minkey = 0;
	}

	public boolean isOnStack() {
		return onStack;
	}

	public void setOnStack(boolean onStack) {
		this.onStack = onStack;
	}

	public void setIsConstant(boolean t) {
		this.isConstant = t;
	}

	public Pointer getDataType(Program currentProgram) {
		if (this.dtype != null)
			return this.dtype;

		ProgramBasedDataTypeManager dm = currentProgram.getDataTypeManager();
		BuiltInDataTypeManager bdm = BuiltInDataTypeManager.getDataTypeManager();
		StructureDataType newStruct = new StructureDataType(new CategoryPath("/struct"), this.name, this.size);
		HashMap<Integer, DataType> sizeLookup = new HashMap<Integer, DataType>();
		sizeLookup.put(1, bdm.getDataType("/char"));
		sizeLookup.put(2, bdm.getDataType("/short"));
		sizeLookup.put(4, bdm.getDataType("/int"));
		sizeLookup.put(8, bdm.getDataType("/longlong"));

		for (Cell c : this.members.values()) {
			int offset = c.getFieldOffset();
			if (c.getOutEdges() != null) {
				Pointer subStructDtype = c.getParent().getDataType(currentProgram);
				newStruct.replaceAtOffset(offset, subStructDtype, currentProgram.getDefaultPointerSize(),
						"entry_" + String.valueOf(offset), "");
			} else {
				if (sizeLookup.containsKey(size))
					newStruct.replaceAtOffset(offset, sizeLookup.get(size), size, "entry_" + String.valueOf(offset),
							"");
				else {
					ArrayDataType arrDtype = new ArrayDataType(sizeLookup.get(1), size, 1);
					newStruct.replaceAtOffset(offset, arrDtype, size, "entry_" + String.valueOf(offset), "");
				}
			}
		}

		dm.addDataType(newStruct, DataTypeConflictHandler.REPLACE_HANDLER);
		this.dtype = dm.getPointer(newStruct, currentProgram.getDefaultPointerSize());
		return this.dtype;
	}

	public void setCollapsed(boolean b) {
		this.collapsed = b;
	}

	public boolean isCollapsed() {
		return this.collapsed;
	}

	public void setOnHeap(boolean b) {
		this.onHeap = b;
	}

	public boolean getOnHeap() {
		return this.onHeap;
	}

	public boolean getIsArg() {
		return this.isArg;
	}

	public void setIsArg(boolean b) {
		this.isArg = b;
	}

	public int getMinOffset() {
		return this.minkey;
	}

	public int getPossibleStride() {
		return this.possibleStride;
	}

	public void setPossibleStride(int s) {
		this.possibleStride = s;
	}

	public Address getLoc() {
		return loc;
	}

	public Graph getGraph() {
		return this.g;
	}

	public void setGraph(Graph newg) {
		this.g = newg;
	}

	public void addConstants(int i) {
		this.setIsConstant(true);
		if (this.isCollapsed())
			return;
		if (i == IndirectCallTargetResolving.TOP) {
			this.collapse(true);
			return;
		}
		if (this.possibleConstant != null && this.possibleConstant.intValue() != i) {
			this.getGraph().changed = true;
			this.collapse(true);
			return;
		}
		this.possibleConstant = i;
	}

	public void clearConstant() {
		this.possibleConstant = null;
	}

	public Integer getConstants() {
		return this.possibleConstant;
	}

	public HashMap<Integer, Cell> getMembers() {
		return members;
	}

	public void setMembers(HashMap<Integer, Cell> members) {
		this.members = members;
	}

	public void extend(int size) {
		if (this.size > size)
			return;
		this.size = size;
	}

	public Cell get(int offset) {
		if (this.isCollapsed()) {
			return this.members.get(0);
		} else if (!this.members.containsKey(offset)) {
			return null;
		} else {
			return this.members.get(offset);
		}
	}

	public Cell getOrCreateCell(int offset) {
		if (this.isCollapsed())
			return this.members.get(0);
		this.extend(offset);
		if (!this.members.containsKey(offset)) {
			Cell newCell = new Cell(this, offset);
			this.insertMember(offset, newCell);
			// for (Cell c : this.subTypes.keySet()) {
			// int field1 = c.getFieldOffset();
			// for (Cell subtype : this.subTypes.get(c)) {
			// DSNode parent2 = subtype.getParent();
			// int field = subtype.getFieldOffset();
			// parent2.getOrCreateCell(offset - field1 + field);
			// }
			// }
		}
		return this.members.get(offset);
	}

	public void removeCell(int offset) {
		if (this.isCollapsed())
			return;
		if (this.members.containsKey(offset))
			this.members.remove(offset);
	}

	public void setArray(boolean isarray) {
		this.isArray = isarray;
	}

	public boolean isArray() {
		return this.isArray;
	}

	public void insertMember(int offset, Cell subStruct) {
		this.members.put(offset, subStruct);
		if (offset < minkey)
			minkey = offset;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public boolean isGlobal() {
		return isGlobal;
	}

	public void setGlobal(boolean isGlobal, Address addr) {
		HashSet<DSNode> visited = new HashSet<DSNode>();
		this.setGlobalAll(isGlobal, visited);
		if (this.loc == null)
			this.loc = addr;
	}

	public void setGlobalAll(boolean isGlobal, HashSet<DSNode> visited) {
		if (visited.contains(this))
			return;
		visited.add(this);
		this.isGlobal = isGlobal;
		for (int i : this.members.keySet()) {
			Cell c = this.members.get(i);
			if (c.getOutEdges() != null) {
				DSNode outEdgeNode = c.getOutEdges().getParent();
				if (outEdgeNode != null) {
					outEdgeNode.setGlobalAll(isGlobal, visited);
				}
			}
		}
	}

	public void collapse(boolean isConstant) {
		HashSet<DSNode> visited = new HashSet<DSNode>();
		this.collapse(isConstant, visited);
	}

	public void collapse(boolean isConstant, HashSet<DSNode> visited) {
		if (isConstant)
			this.possibleConstant = IndirectCallTargetResolving.TOP;
		else
			this.possibleConstant = null;
		if (this.collapsed)
			return;
		this.collapsed = true;
		Graph thisg = this.getGraph();
		Function f = thisg.getF();
		thisg.changed = true;

		if (this.hasOut())
			this.collectMemberTypes();

		HashSet<Integer> allcell = new HashSet<Integer>();
		HashSet<Address> possiblePointers = new HashSet<Address>();
		allcell.addAll(this.getMembers().keySet());
		Cell minCell = this.getMembers().get(0);
		if (minCell == null) {
			minCell = new Cell(this, 0);
		}
		Cell e = minCell.getOutEdges();

		for (Integer cell : allcell) {
			if (this.getMembers().get(cell) != null) {
				possiblePointers.addAll(this.getMembers().get(cell).getPossiblePointersWithLoading(visited));
			}
		}
		minCell.addAllPointers(possiblePointers);

		for (Integer cell : allcell) {
			if (cell == 0)
				continue;
			Cell thiscell = this.getMembers().get(cell);
			if (thiscell != null) {
				// merge out edge
				Cell out = thiscell.getOutEdges();

				// we don't include the cell's out to this if it is a constant
				if (out != null && out.getParent() != null && out.getParent().isConstant) {
					this.getMembers().remove(cell);
					// assign it to stand alone node
					DSNode newParent = new DSNode(thiscell.getParent().loc, thisg);
					newParent.insertMember(thiscell.getFieldOffset(), thiscell);
					thiscell.setParent(newParent);
					if (cell == 0)
						minCell = new Cell(this, 0);
					continue;
				}

				if (out != null && e == null) {
					e = out;
					out.removeInEdges(thiscell);
					thiscell.removeOutEdges();
					minCell.setOutEdges(e);
				} else if (out != null) {
					out.removeInEdges(thiscell);
					thiscell.removeOutEdges();
					e = e.merge(out, visited);
				}

				// merge the in edges
				HashSet<Cell> inedges = new HashSet<Cell>();
				inedges.addAll(thiscell.getInEdges());
				for (Cell inEdge : inedges) {
					// if inedge is collapsed, the original cell has been deleted, so we get the
					// cell 0
					if (inEdge.getParent() == null)
						continue;
					if (inEdge.getParent().isCollapsed()) {
						thiscell.removeInEdges(inEdge);
						inEdge.removeOutEdges();
						if (inEdge.getParent().get(0) == null)
							System.out.println("debug here");
						inEdge.getParent().get(0).setOutEdges(minCell);
					} else
						inEdge.setOutEdges(minCell);
				}
				// thiscell.getInEdges().clear();

				for (Varnode inEdge : thiscell.getInEvEdges()) {
					Function inEdgeFunc = inEdge.getHigh().getHighFunction().getFunction();
					if (inEdgeFunc != null) {
						Graph inEdgeG = thisg.getAllLocalGraphs().get(inEdgeFunc);
						if (inEdgeG != null)
							inEdgeG.setEv(inEdge, minCell);
						continue;
					}
				}
				// thiscell.getInEvEdges().clear();

				// merge callsites
				for (Pair<CallSiteNode, String> cs : thiscell.getInCallSite()) {
					CallSiteNode csn = cs.getK();
					String s = cs.getV();
					if (s == "ret") {
						csn.setMember(0, minCell);
					} else if (s == "func") {
						csn.setMember(1, minCell);

					} else {
						int argind = Integer.valueOf(s);
						csn.setMember(argind + 1, minCell);
					}
					minCell.addInCallSite(cs);
				}
				// thiscell.getInCallSite().clear();

				HashSet<Address> allglobals = new HashSet<Address>(thiscell.getGlobalAddrs());
				for (Address global : allglobals) {
					thiscell.getGlobalAddrs().remove(global);
					this.getGraph().getAllGlobals().put(global, minCell);
					minCell.getGlobalAddrs().add(global);
				}

				if (thiscell.getCalleeArgLabel() != null) {
					for (String label : thiscell.getCalleeArgLabel()) {
						thisg.getCalleeargs().put(label, minCell);
						minCell.addCalleeArgLabel(label);
					}
					thiscell.getCalleeArgLabel().clear();
				}

				if (thiscell.getStackLocs().containsKey(f)) {
					HashSet<Integer> stackLocSet = new HashSet<Integer>();
					stackLocSet.addAll(thiscell.getStackLocs(f));
					for (Integer stackLoc : stackLocSet) {
						thiscell.getStackLocs(f).remove(stackLoc);
						minCell.addStackLocs(f, stackLoc);
						thisg.stackObj.put(stackLoc, minCell);
					}
				}

				if (thiscell.isRSP(f)) {
					int offset = thiscell.getRSPOffset(f);
					thiscell.setRSPOffset(f, null);
					minCell.setRSPOffset(f, offset);
				}

				for (Function key : thiscell.getStackLocs().keySet()) {
					HashSet<Integer> stackLocSet = new HashSet<Integer>();
					stackLocSet.addAll(thiscell.getStackLocs(key));
					if (stackLocSet.size() > 0) {
						for (Integer stackLoc : stackLocSet) {
							thiscell.getStackLocs(key).remove(stackLoc);
							minCell.addStackLocs(key, stackLoc);
							Graph curg = g.getAllLocalGraphs().get(key);
							curg.stackObj.put(stackLoc, minCell);
						}
					}
				}

				for (Function key : thiscell.getRSPOffset().keySet()) {
					if (thiscell.isRSP(key)) {
						int offset = thiscell.getRSPOffset(key);
						thiscell.setRSPOffset(key, null);
						minCell.setRSPOffset(key, offset);
					}
				}

				if (thiscell.getInMemEdges() != null) {
					Cell inMemEdgeMerged = thiscell.getInMemEdges();
					thiscell.setInMemEdges(null);
					minCell.setInMemEdges(inMemEdgeMerged);
					inMemEdgeMerged.setOutMemEdge(minCell);
				}

				if (thiscell.getOutMemEdge() != null) {
					Cell outMemEdgeMerged = thiscell.getOutMemEdge();
					thiscell.setOutMemEdge(null);
					minCell.setOutMemEdge(outMemEdgeMerged);
					outMemEdgeMerged.setInMemEdges(minCell);
				}

				minCell.getReadFunc().addAll(thiscell.getReadFunc());
				minCell.getWriteFunc().addAll(thiscell.getWriteFunc());
			}

			if (cell != 0) {
				this.getMembers().remove(cell);
				// thiscell.setParent(null);
			}
		}

		if (e != null && this.get(0) != null)
			this.get(0).setOutEdges(e);

		this.minkey = 0;
	}

	public void collectMemberTypes() {
		for (int i : this.members.keySet()) {
			Cell cell = this.members.get(i);
			Cell outEdge = cell.getOutEdges();
			if (outEdge != null && outEdge.getParent() != null) {
				if (outEdge.getParent().hasOut())
					this.memberType.put(i, true);
				else if (outEdge.getParent().getIsConstant())
					this.memberType.put(i, false);
				else
					this.memberType.put(i, null);
			}
		}
	}

	public void getPossiblePointersWithLoading(HashSet<DSNode> visitedDSNode) {
		int minkey = this.getMinOffset();

		if (this.get(minkey) != null) {
			HashSet<Address> ptrs = new HashSet<Address>();
			ptrs.addAll(this.get(minkey).possiblePointers);
			if (ptrs.size() == 0)
				return;

			HashSet<Integer> keyset = new HashSet<Integer>();
			keyset.addAll(this.getMembers().keySet());
			for (int field : keyset) {
				Cell thiscell = this.get(field);
				if (thiscell == null)
					continue;
				for (Address ptr : ptrs) {
					try {
						thiscell.addPointersWithLoading(ptr.add(field - minkey), visitedDSNode);
					} catch (ghidra.program.model.address.AddressOutOfBoundsException e) {
						DebugUtil.print("Address out of bounds");
					}
				}
			}
		}
		return;
	}

	public DSNode clone(Graph newg) {
		DSNode newDS = new DSNode(this.size, this.loc, newg);
		HashSet<Integer> keyset = new HashSet<Integer>();
		keyset.addAll(this.members.keySet());
		for (int i : keyset) {
			Cell thisCell = this.members.get(i);
			Cell copiedCell = new Cell(newDS, thisCell.getFieldOffset());
			copiedCell.addAllPointers(thisCell.getPossiblePointers());
			copiedCell.getWriteFunc().addAll(thisCell.getWriteFunc());
			copiedCell.getReadFunc().addAll(thisCell.getReadFunc());
			newDS.insertMember(i, copiedCell);
			newDS.memberSize.put(i, this.memberSize.get(i));
		}
		newDS.isArray = this.isArray;
		newDS.collapsed = this.collapsed;
		newDS.onHeap = this.onHeap;
		newDS.isArg = this.isArg;
		newDS.onStack = this.onStack;

		newDS.possibleConstant = this.possibleConstant;
		newDS.mergedWith.addAll(this.mergedWith);
		for (AllocSite as : this.allocationSites) {
			AllocSite asCopied = as.deepcopy();
			asCopied.addCallpath(newg.getF());
			newDS.allocationSites.add(asCopied);
		}
		return newDS;
	}

	public DSNode deepCopy(Map<DSNode, DSNode> isomorphism, Graph newG, CallSiteNode cs) {
		if (this.isGlobal())
			return this;
		DSNode newDS = isomorphism.get(this);
		if (newDS == null) {
			newDS = this.clone(newG);
			isomorphism.put(this, newDS);

			HashSet<CallSiteNode> csnodeset = new HashSet<CallSiteNode>();
			for (CallSiteNode csn : this.getGraph().getCallNodes().values()) {
				if (!csn.getResolved() && csn.isIndirect)
					csnodeset.add(csn);
			}

			HashSet<Integer> indexes = new HashSet<Integer>();
			indexes.addAll(this.members.keySet());
			for (int i : indexes) {
				Cell c = this.members.get(i);
				Cell newc = newDS.getMembers().get(i);
				if (c == null || newc == null)
					continue;

				if (c.getOutEdges() != null) {
					DSNode outEdgeNode = c.getOutEdges().getParent();
					if (outEdgeNode != null) {
						int outEdgeOffset = c.getOutEdges().getFieldOffset();
						DSNode newOutEdgeNode = outEdgeNode.deepCopy(isomorphism, newG, cs);
						newc.addOutEdges(newOutEdgeNode.get(outEdgeOffset));
					}
				}

				HashSet<Cell> inedges = new HashSet<Cell>();
				inedges.addAll(c.getInEdges());
				for (Cell inEdge : inedges) {
					DSNode inEdgeNode = inEdge.getParent();
					if (inEdgeNode == null)
						continue;
					int inEdgeOffset = inEdge.getFieldOffset();
					DSNode newInEdgeNode = inEdgeNode.deepCopy(isomorphism, newG, cs);
					newc.addInEdges(newInEdgeNode.get(inEdgeOffset));
				}
				// copy the relation between callsitenode and cell
				HashSet<Pair<CallSiteNode, String>> csNodePairs = new HashSet<Pair<CallSiteNode, String>>();
				csNodePairs.addAll(c.getInCallSite());
				for (Pair<CallSiteNode, String> csNodePair : csNodePairs) {
					CallSiteNode csnode = csNodePair.getK();
					String s = csNodePair.getV();
					if (!csnodeset.contains(csnode) && !csnode.isGlobalAddr)
						continue;
					// ArrayList<Address> cp = new ArrayList<Address>();
					// cp.addAll(cs.getCallPath());
					// cp.retainAll(csnode.getCallPath());
					//
					// if (cs.getCallPath().contains(csnode.getLoc()))
					// continue;

					CallSiteNode newCSNode = csnode.deepCopy(isomorphism, newG, cs);
					if (newCSNode == null)
						continue;
					newc.addInCallSite(new Pair<CallSiteNode, String>(newCSNode, s));
				}

			}

		}

		return newDS;
	}

	public String toString() {
		HashSet<DSNode> visited = new HashSet<DSNode>();
		return this.toString("", visited);
	}

	public String toString(String indent, HashSet<DSNode> visited) {
		if (visited.contains(this))
			return "";
		String s = indent + "Node@";
		if (argNo == null && this.loc != null)
			s += this.loc.toString();
		else if (argNo != null)
			s += this.argNo;

		if (this.isGlobal)
			s += " Global";
		if (this.isCollapsed())
			s += " Collapsed";
		s += "\n";

		visited.add(this);
		HashSet<Integer> keyset = new HashSet<Integer>();
		keyset.addAll(this.members.keySet());
		for (int i : this.members.keySet()) {
			s += indent + "offset " + String.valueOf(i);
			Cell curCell = this.members.get(i);
			if (curCell != null && curCell.getPossiblePointers().size() > 0)
				s += " Const " + curCell.getPossiblePointers().toString();
			if (this.members.get(i) == null) {
				s += "\n" + indent + "    <null, 0>\n";
				continue;
			}
			Cell c = curCell.getOutEdges();
			if (c == null) {
				s += "\n" + indent + "    <null, 0>\n";
				continue;
			}
			DSNode parent = c.getParent();
			if (parent == null) {
				s += "\n" + indent + "    <null, 0>\n";
				continue;
			}
			int offset = c.getFieldOffset();
			s += " -> offset" + String.valueOf(offset) + "\n";
			s += parent.toString(indent + "    ", visited);
		}

		return s;
	}

	public String getArgNo() {
		return argNo;
	}

	public void setArgNo(String argNo) {
		this.argNo = argNo;
	}

	public boolean hasCycle(HashSet<Address> currentAddrs) {
		for (Cell c : this.members.values()) {
			Cell outE = c.getOutEdges();
			if (outE == null || outE.getParent() == null)
				continue;
			if (currentAddrs.contains(outE.getParent().getLoc()))
				return true;
			Address cloc = outE.getParent().getLoc();
			currentAddrs.add(cloc);
			if (outE.getParent().hasCycle(currentAddrs))
				return true;
			currentAddrs.remove(cloc);
		}
		return false;
	}

	public void getDesendants(HashSet<DSNode> des) {
		des.add(this);
		for (Cell c : this.members.values()) {
			Cell outCell = c.getOutEdges();
			if (outCell != null && outCell.getParent() != null && !des.contains(outCell.getParent()))
				outCell.getParent().getDesendants(des);
		}
	}

	public void getPreDesendants(HashSet<DSNode> des) {
		des.add(this);
		for (Cell c : this.members.values()) {
			HashSet<Cell> inCell = c.getInEdges();
			for (Cell in : inCell) {
				if (in != null && in.getParent() != null && !des.contains(in.getParent()))
					in.getParent().getPreDesendants(des);
			}
		}
	}

	public void addMergedWith(Address loc2) {
		if (!mergedWith.contains(loc2))
			mergedWith.add(loc2);
	}

	public boolean hasOut() {
		boolean hasOut = false;
		for (Cell c : this.members.values()) {
			if (c.getOutEdges() != null)
				hasOut |= true;
		}
		if (hasOut)
			this.isConstant = false;
		return hasOut;
	}

	public boolean getIsConstant() {
		this.hasOut();
		return this.isConstant;
	}

	public HashMap<Integer, Boolean> getMemberType() {
		return memberType;
	}

	public void setMemberType(HashMap<Integer, Boolean> memberType) {
		this.memberType = memberType;
	}

	public void addMemberType(int offset, boolean type) {
		this.memberType.put(offset, type);
	}

	public void addSubTypeCell(Cell cell, Cell input) {
		if (!this.subTypes.containsKey(cell))
			this.subTypes.put(cell, new HashSet<Cell>());
		this.subTypes.get(cell).add(input);
	}

	public void addSuperTypeCell(Cell cell, Cell input) {
		if (!this.superTypes.containsKey(cell))
			this.superTypes.put(cell, new HashSet<Cell>());
		this.superTypes.get(cell).add(input);
	}

	public HashSet<Function> getReadFunc() {
		HashSet<Function> accessFunc = new HashSet<Function>();
		for (Cell c : this.members.values()) {
			accessFunc.addAll(c.getReadFunc());
		}
		return accessFunc;
	}

	public HashSet<Function> getWriteFunc() {
		HashSet<Function> accessFunc = new HashSet<Function>();
		for (Cell c : this.members.values()) {
			accessFunc.addAll(c.getWriteFunc());
		}
		return accessFunc;
	}

	public HashSet<Function> getAllReadFunc() {
		HashSet<Function> accessFunc = new HashSet<Function>();
		HashSet<DSNode> des = new HashSet<DSNode>();
		this.getDesendants(des);
		for (DSNode n : des) {
			accessFunc.addAll(n.getReadFunc());
		}
		return accessFunc;
	}

	public HashSet<Function> getAllWriteFunc() {
		HashSet<Function> accessFunc = new HashSet<Function>();
		HashSet<DSNode> des = new HashSet<DSNode>();
		this.getDesendants(des);
		for (DSNode n : des) {
			accessFunc.addAll(n.getWriteFunc());
		}
		return accessFunc;
	}
}

class CallSiteNode extends DSNode {
	private ArrayList<Address> callPath;
	private String tokens;
	private boolean resolved;
	public boolean isIndirect;
	public boolean isGlobalAddr;
	public int numIndirectCall;

	public CallSiteNode() {
		this.members = new HashMap<Integer, Cell>();
		this.callPath = new ArrayList<Address>();
		this.resolved = false;
		this.isIndirect = false;
		this.isGlobalAddr = false;
		this.numIndirectCall = 0;
	}

	public void setResolved(boolean b) {
		this.resolved = b;
	}

	public boolean getResolved() {
		return this.resolved;
	}

	public CallSiteNode(Cell returnCell, Cell func, ArrayList<Cell> args, Address loc, Graph newg) {
		this.members = new HashMap<Integer, Cell>();
		this.members.put(0, returnCell);
		if (returnCell != null)
			returnCell.addInCallSite(new Pair<CallSiteNode, String>(this, "ret"));
		this.members.put(1, func);
		this.loc = loc;
		func.addInCallSite(new Pair<CallSiteNode, String>(this, "func"));
		for (int i = 0; i < args.size(); i++) {
			this.members.put(i + 2, args.get(i));
			args.get(i).addInCallSite(new Pair<CallSiteNode, String>(this, String.valueOf(i + 1)));
		}
		this.setGraph(newg);
		this.callPath = new ArrayList<Address>();
		callPath.add(loc);
		this.resolved = false;
		this.isIndirect = false;
		this.isGlobalAddr = false;
		this.numIndirectCall = 0;
	}

	public void setTokens(String t) {
		tokens = t;
	}

	public String getTokens() {
		return tokens;
	}

	public void update(Cell returnCell, Cell func, ArrayList<Cell> args) {
		if (this.members.get(0) != returnCell) {
			this.members.put(0, returnCell);
			if (returnCell != null)
				returnCell.addInCallSite(new Pair<CallSiteNode, String>(this, "ret"));
		}

		if (this.members.get(1) != func) {
			this.members.put(1, func);
			func.addInCallSite(new Pair<CallSiteNode, String>(this, "func"));
		}

		for (int i = 0; i < args.size(); i++) {
			if (this.members.get(i + 2) == args.get(i))
				continue;
			this.members.put(i + 2, args.get(i));
			args.get(i).addInCallSite(new Pair<CallSiteNode, String>(this, String.valueOf(i + 1)));
		}
	}

	public Cell getReturn() {
		return this.members.get(0);
	}

	public Cell getFunc() {
		return this.members.get(1);
	}

	public Cell getArgI(int i) {
		return this.members.get(i + 2);
	}

	public void setMember(int i, Cell c) {
		this.members.put(i, c);
	}

	public CallSiteNode deepCopy(Map<DSNode, DSNode> isomorphism, Graph newg, CallSiteNode cs) {
		CallSiteNode newCS = (CallSiteNode) isomorphism.get(this);
		if (newCS != null)
			return newCS;

		ArrayList<Address> newCallPath = new ArrayList<Address>();
		newCallPath.addAll(cs.callPath);
		newCallPath.addAll(this.callPath);

		// if (cs.numIndirectCall + this.numIndirectCall > 1)
		// return null;

		newCS = new CallSiteNode();
		isomorphism.put(this, newCS);
		newCS.setGraph(newg);

		newCS.loc = this.loc;
		newCS.callPath = new ArrayList<Address>();
		newCS.callPath.addAll(newCallPath);
		newCS.numIndirectCall = cs.numIndirectCall + this.numIndirectCall;

		newCS.tokens = tokens;
		newCS.resolved = this.resolved;
		newCS.isIndirect = isIndirect;
		DebugUtil.print("Added CS " + newCS);
		for (int i : this.members.keySet()) {
			Cell copiedCell = null;
			Cell thisCell = this.members.get(i);
			if (thisCell != null) {
				DSNode thisParent = thisCell.getParent();
				if (thisParent != null) {
					DSNode copiedParent = thisParent.deepCopy(isomorphism, newg, cs);
					copiedCell = copiedParent.get(thisCell.getFieldOffset());
					if (copiedParent.isGlobal()) {
						// if is global, this relation is not added in DSNode.deepCopy
						String s;
						if (i == 0)
							s = "ret";
						else if (i == 1)
							s = "func";
						else
							s = String.valueOf(i + 1);
						copiedCell.addInCallSite(new Pair<CallSiteNode, String>(newCS, s));
					}
				}
			}
			newCS.members.put(i, copiedCell);
		}
		newCS = newg.addCallNodesToTmp(newCS.getLoc(), newCS);
		return newCS;
	}

	public String toString() {
		String s = "Node@";
		for (Address f : this.callPath)
			s += f.toString() + "@";
		s += tokens;
		// s += members.toString();
		return s;
	}

	public Graph getGraph() {
		return this.g;
	}

	public Address getLoc() {
		return this.loc;
	}

	public ArrayList<Address> getCallPath() {
		return this.callPath;
	}

}

class Cell {
	private HashSet<Cell> inEdges;
	private HashSet<Varnode> inEvEdges;
	private HashSet<Pair<CallSiteNode, String>> inCallsite;
	private Cell outEdge;
	private int fieldOffset;
	private DSNode parent;
	private HashSet<String> accessMode;
	public HashSet<Address> possiblePointers;
	private HashSet<String> allInCallSites;
	private HashSet<Address> globalAddrs;
	private HashMap<Function, HashSet<Integer>> stackLocs;
	private Cell inMemEdge;
	private Cell outMemEdge;
	private HashSet<String> calleeArgLabel; // used for cloned Cells
	private HashMap<Function, Integer> isRSP;
	public boolean isLoopVariant;
	private HashSet<Function> readFunc;

	private HashSet<Function> writeFunc;

	public Cell(DSNode parent, int fieldOffset) {
		this.parent = parent;
		this.fieldOffset = fieldOffset;
		this.inEdges = new HashSet<Cell>();
		this.inEvEdges = new HashSet<Varnode>();
		this.accessMode = new HashSet<String>();
		if (parent != null)
			parent.insertMember(fieldOffset, this);
		this.inCallsite = new HashSet<Pair<CallSiteNode, String>>();
		this.possiblePointers = new HashSet<Address>();
		this.stackLocs = new HashMap<Function, HashSet<Integer>>();
		this.allInCallSites = new HashSet<String>();
		this.globalAddrs = new HashSet<Address>();
		this.isRSP = new HashMap<Function, Integer>();
		this.isLoopVariant = false;
		this.readFunc = new HashSet<Function>();
		this.writeFunc = new HashSet<Function>();
	}

	public void setSubTypeRelation(Cell input) {
		DSNode parent1 = this.parent;
		DSNode parent2 = input.parent;
		if (parent1 == null || parent2 == null || parent1 == parent2)
			return;
		parent1.addSubTypeCell(this, input);
		parent2.addSuperTypeCell(input, this);
	}

	public HashSet<Function> getReadFunc() {
		return readFunc;
	}

	public void setReadFunc(Function readFunc) {
		this.readFunc.add(readFunc);
	}

	public HashSet<Function> getWriteFunc() {
		return writeFunc;
	}

	public void setWriteFunc(Function writeFunc) {
		this.writeFunc.add(writeFunc);
	}

	public HashSet<String> getCalleeArgLabel() {
		return calleeArgLabel;
	}

	public void clearCalleeArgLabel() {
		this.calleeArgLabel = null;
	}

	public void addCalleeArgLabel(String argLabel) {
		if (this.calleeArgLabel == null)
			this.calleeArgLabel = new HashSet<String>();
		this.calleeArgLabel.add(argLabel);
	}

	public HashSet<Integer> getStackLocs(Function func) {
		return stackLocs.get(func);
	}

	public HashMap<Function, HashSet<Integer>> getStackLocs() {
		return stackLocs;
	}

	public void addStackLocs(Function func, Integer stackLocs) {
		if (!this.stackLocs.containsKey(func))
			this.stackLocs.put(func, new HashSet<Integer>());
		HashSet<Integer> locs = this.stackLocs.get(func);
		locs.add(stackLocs);
	}

	public Cell getInMemEdges() {
		return inMemEdge;
	}

	public void setInMemEdges(Cell inMemEdges) {
		this.inMemEdge = inMemEdges;
	}

	public Cell getOutMemEdge() {
		return outMemEdge;
	}

	public void setOutMemEdge(Cell outMemEdge) {
		this.outMemEdge = outMemEdge;
	}

	public void setRSPOffset(Function func, Integer b) {
		this.isRSP.put(func, b);
	}

	public int getRSPOffset(Function func) {
		return this.isRSP.get(func);
	}

	public HashMap<Function, Integer> getRSPOffset() {
		return this.isRSP;
	}

	public boolean isRSP(Function func) {
		return this.isRSP.get(func) != null;
	}

	public void addInCallSite(Pair<CallSiteNode, String> cs) {
		// String index = "";
		// if (cs.getK().getLoc() != null)
		// index += cs.getK().getLoc().toString();
		// else
		// index += "null";
		// index += cs.getV();
		// if (this.allInCallSites.contains(index) && cs.getK().getLoc() != null)
		//// System.out.println("duplicate");
		// return;
		// else {
		this.inCallsite.add(cs);
		// this.allInCallSites.add(index);
		// }
	}

	public HashSet<Pair<CallSiteNode, String>> getInCallSite() {
		return this.inCallsite;
	}

	public HashSet<Address> getGlobalAddrs() {
		return this.globalAddrs;
	}

	public Cell merge(Cell cell) {
		if (this == cell) {
			return this;
		}
		if (cell == null)
			return this;
		DSNode parent2 = this.parent;
		DSNode parent1 = cell.getParent();
		if (parent1 == null)
			return this;
		if (parent2 == null) {
			return cell.merge(this);
		}
		if (parent1.getIsConstant() || parent2.getIsConstant())
			return this;
		HashSet<DSNode> notDelete = new HashSet<DSNode>();
		HashSet<DSNode> visitedDSNode = new HashSet<DSNode>();

		Cell retCell = this.merge(cell, visitedDSNode);

		// delete merged cell
		if (retCell != null && retCell.getParent() != null) {
			HashSet<DSNode> des = new HashSet<DSNode>();
			retCell.getParent().getDesendants(des);
			notDelete.addAll(des);
			HashSet<DSNode> pre = new HashSet<DSNode>();
			retCell.getParent().getPreDesendants(pre);
			notDelete.addAll(pre);
		}

		for (DSNode d : visitedDSNode) {
			if (!notDelete.contains(d)) {
				boolean isGlobal = false;
				for (Cell c : d.members.values()) {
					if (c.getGlobalAddrs().size() > 0)
						isGlobal = true;
				}
				if (isGlobal)
					continue;
				// DebugUtil.print("Deleted " + d.toString());
				// HashSet<DSNode> desthis = new HashSet<DSNode>();
				// if (d.toString().contains("805c50c")) {
				// this.getParent().getPreDesendants(desthis);
				// }
				for (Cell c : d.members.values()) {
					c.removeOutEdges();
					c.getInEdges().clear();
					c.getInEvEdges().clear();
					c.getInCallSite().clear();
					c.getPossiblePointers().clear();
					c.setParent(null);
				}
				d.members.clear();
			}
		}
		return retCell;
	}

	public Cell merge(Cell cell, HashSet<DSNode> visitedDSNode) {
		if (this == cell) {
			return this;
		}
		if (cell == null)
			return this;
		DSNode parent2 = this.parent;
		DSNode parent1 = cell.getParent();
		if (parent1 == null)
			return this;
		if (parent2 == null) {
			return cell.merge(this);
		}
		if (parent1.getIsConstant() || parent2.getIsConstant())
			return this;
		// TODO: handle merge conflicts
		if (parent2 == parent1) {
			parent2.collapse(false, visitedDSNode);
			return parent2.get(0);
		}

		HashMap<Cell, Cell> isomorphism = new HashMap<Cell, Cell>();
		createIsomorphism(this, cell, isomorphism, new HashSet<DSNode>());
		Cell thisnew = isomorphism.get(cell);
		Cell retCell = thisnew.mergeContent(cell, visitedDSNode);
		return retCell;
	}

	public void createIsomorphism(Cell targetCell, Cell mergedCell, HashMap<Cell, Cell> isomorphism,
			HashSet<DSNode> visitedDSNode) {
		DSNode parent2 = targetCell.parent;
		DSNode parent1 = mergedCell.getParent();
		if (parent1 == null || parent2 == null)
			return;
		if (visitedDSNode.contains(parent2))
			return;
		visitedDSNode.add(parent2);

		if (parent1.isCollapsed()) {
			parent2.collapse(false, visitedDSNode);
			targetCell = parent2.get(0);
		}

		if (isomorphism.get(mergedCell) != null && isomorphism.get(mergedCell) == targetCell)
			return;
		else if (isomorphism.get(mergedCell) != null) {
			targetCell.merge(isomorphism.get(mergedCell), new HashSet<DSNode>());
			isomorphism.put(mergedCell, targetCell);
		} else
			isomorphism.put(mergedCell, targetCell);

		int field2 = targetCell.getFieldOffset();
		int field1 = mergedCell.getFieldOffset();
		ArrayList<Integer> keyset = new ArrayList<Integer>();
		keyset.addAll(parent1.getMembers().keySet());
		Graph g = targetCell.getGraph();
		g.changed = true;
		for (int field : keyset) {
			parent2 = isomorphism.get(mergedCell).parent;
			Cell cell1 = parent1.get(field);
			Cell cell2 = parent2.getOrCreateCell(field + field2 - field1);
			if (cell2 == null || cell1 == null)
				continue;
			// if (isomorphism.get(cell1) != null && isomorphism.get(cell1) == cell2)
			// continue;
			// else if (isomorphism.get(mergedCell) != null) {
			// cell2.mergeContent(isomorphism.get(cell1), new HashSet<DSNode>());
			// isomorphism.put(cell1, cell2);
			// } else
			isomorphism.put(cell1, cell2);

			// merge the out edges of different corresponding fields
			Cell outCell2 = cell2.getOutEdges();
			Cell outCell1 = cell1.getOutEdges();
			if (outCell2 != null && outCell1 != null)
				createIsomorphism(outCell2, outCell1, isomorphism, visitedDSNode);
		}

	}

	public Cell mergeContent(Cell cell, HashSet<DSNode> visitedDSNode) {
		if (this == cell) {
			return this;
		}
		if (cell == null)
			return this;
		DSNode parent2 = this.parent;
		DSNode parent1 = cell.getParent();
		if (parent1 == null)
			return this;
		if (parent2 == null) {
			return cell;
		}
		if (parent1.getIsConstant() || parent2.getIsConstant())
			return this;
		if (visitedDSNode.contains(parent1)) // in case parent1 has been visited
			return this;
		visitedDSNode.add(parent1);
		int field2 = this.getFieldOffset();
		int field1 = cell.getFieldOffset();

		// // TODO: handle merge conflicts
		if (parent2 == parent1) {
			parent2.collapse(false, visitedDSNode);
			return parent2.get(0);
		}
		// if cell is collapsed, set this to collapsed as well
		if (parent1.isCollapsed())
			parent2.collapse(false, visitedDSNode);

		if (this.parent == null)
			return this;
		ArrayList<Integer> keyset = new ArrayList<Integer>();
		keyset.addAll(parent1.getMembers().keySet());
		Graph g = this.getGraph();
		Graph mergedCellG = parent1.g;
		g.changed = true;
		Program currProg = g.getF().getProgram();
		for (int field : keyset) {
			Cell mergedCell = parent1.get(field);
			Cell cell2 = parent2.getOrCreateCell(field + field2 - field1);
			if (cell2 == null || mergedCell == null)
				continue;

			// merge the out edges of different corresponding fields
			Cell outCell2 = cell2.getOutEdges();
			Cell outCell1 = mergedCell.getOutEdges();
			if (outCell2 != null)
				outCell2.mergeContent(outCell1, visitedDSNode);
			else if (outCell1 != null)
				cell2.setOutEdges(outCell1);
			if (outCell1 != null)
				outCell1.removeInEdges(mergedCell);

			// merge the in edges
			HashSet<Cell> inedges = new HashSet<Cell>();
			inedges.addAll(mergedCell.getInEdges());
			for (Cell inEdge : inedges) {
				// if inedge is collapsed, the original cell has been deleted, so we get the
				// cell 0
				if (inEdge.getParent() == null)
					continue;
				if (inEdge.getParent().isCollapsed()) {
					// mergedCell.removeInEdges(inEdge);
					inEdge.removeOutEdges();
					if (inEdge.getParent().get(0) == null)
						continue;
					inEdge.getParent().get(0).setOutEdges(cell2);
				} else
					inEdge.setOutEdges(cell2);
			}

			for (Varnode inEdge : mergedCell.getInEvEdges()) {
				Function inEdgeFunc = inEdge.getHigh().getHighFunction().getFunction();
				if (inEdgeFunc != null) {
					Graph inEdgeG = g.getAllLocalGraphs().get(inEdgeFunc);
					if (inEdgeG != null)
						inEdgeG.setEv(inEdge, cell2);
					continue;
				}
			}

			// merge the callsite info
			HashSet<Pair<CallSiteNode, String>> incallsites = new HashSet<Pair<CallSiteNode, String>>();
			incallsites.addAll(cell2.getInCallSite());
			for (Pair<CallSiteNode, String> cs : incallsites) {
				CallSiteNode csn = cs.getK();
				String s = cs.getV();
				if (s == "func" && mergedCell.getPossiblePointersWithLoading(visitedDSNode).size() > 0
						&& csn.isIndirect) {
					HashSet<Address> fps = IndirectCallTargetResolving
							.getPossibleFuncPointer(mergedCell.getPossiblePointers(), currProg);
					if (fps.size() > 0) {
						Instruction instr = currProg.getListing().getInstructionAt(csn.getLoc());
						if (instr != null) {
							boolean resolvedNew = false;
							HashSet<Address> existingTargets = new HashSet<Address>();
							if (g.getCallNodes(csn.getLoc()) != null)
								existingTargets.addAll(g.getCallNodes(csn.getLoc()).getFunc().getPossiblePointers());
							if (g.getTmpCallNodes(csn.getLoc()) != null)
								for (CallSiteNode n : g.getTmpCallNodes(csn.getLoc())) {
									existingTargets.addAll(n.getFunc().getPossiblePointers());
								}
							// for (Reference r : instr.getOperandReferences(0)) {
							// existingTargets.add(r.getToAddress());
							// }
							for (Address address : fps) {
								if (!existingTargets.contains(address)) {
									resolvedNew = true;
									int as = currProg.getAddressFactory().getAddressSpace("ram").getSpaceID();
									address = currProg.getAddressFactory().getAddress(as, address.getOffset());
									instr.addOperandReference(0, address, RefType.COMPUTED_CALL,
											SourceType.USER_DEFINED);
								}
							}

							if (resolvedNew) {
								csn.getGraph().resolvedNewCallSite = true;
								DebugUtil.print("Solved1 " + csn.toString() + " -> " + fps.toString());
								BufferedWriter out;
								try {
									out = new BufferedWriter(new OutputStreamWriter(
											new FileOutputStream(IndirectCallTargetResolving.outPath, true)));
									out.write(csn.toString() + "@" + String.valueOf(fps.size()) + "@" + fps.toString());
									out.newLine();
									out.close();
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}

					}
				}
			}

			for (Pair<CallSiteNode, String> cs : mergedCell.getInCallSite()) {
				CallSiteNode csn = cs.getK();
				String s = cs.getV();
				if (s == "ret") {
					csn.setMember(0, cell2);
				} else if (s == "func") {
					csn.setMember(1, cell2);
					if (cell2.getPossiblePointersWithLoading(visitedDSNode).size() > 0 && csn.isIndirect) {
						HashSet<Address> fps2 = IndirectCallTargetResolving
								.getPossibleFuncPointer(cell2.getPossiblePointers(), currProg);
						if (fps2.size() > 0) {
							Instruction instr = currProg.getListing().getInstructionAt(csn.getLoc());
							if (instr != null) {
								boolean resolvedNew = false;
								HashSet<Address> existingTargets = new HashSet<Address>();
								if (g.getCallNodes(csn.getLoc()) != null)
									existingTargets
											.addAll(g.getCallNodes(csn.getLoc()).getFunc().getPossiblePointers());
								if (g.getTmpCallNodes(csn.getLoc()) != null)
									for (CallSiteNode n : g.getTmpCallNodes(csn.getLoc())) {
										existingTargets.addAll(n.getFunc().getPossiblePointers());
									}
								// for (Reference r : instr.getOperandReferences(0)) {
								// existingTargets.add(r.getToAddress());
								// }
								for (Address address : fps2) {
									if (!existingTargets.contains(address)) {
										resolvedNew = true;
										int as = currProg.getAddressFactory().getAddressSpace("ram").getSpaceID();
										address = currProg.getAddressFactory().getAddress(as, address.getOffset());
										instr.addOperandReference(0, address, RefType.COMPUTED_CALL,
												SourceType.USER_DEFINED);
									}
								}
								if (resolvedNew) {
									csn.getGraph().resolvedNewCallSite = true;
									DebugUtil.print("Solved2 " + csn.toString() + " -> " + fps2.toString());
									BufferedWriter out;
									try {
										out = new BufferedWriter(new OutputStreamWriter(
												new FileOutputStream(IndirectCallTargetResolving.outPath, true)));
										out.write(csn.toString() + "@" + String.valueOf(fps2.size()) + "@"
												+ fps2.toString());
										out.newLine();
										out.close();
									} catch (Exception e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

								}
							}
						}
					}

				} else {
					int argind = Integer.valueOf(s);
					csn.setMember(argind + 1, cell2);
				}
				cell2.addInCallSite(cs);
			}

			if (cell2 != null && mergedCell != null && mergedCell.getPossiblePointers().size() > 0) {
				cell2.addAllPointers(mergedCell.getPossiblePointers());
			}

			HashSet<Address> bindedGlobals = new HashSet<Address>();
			bindedGlobals.addAll(mergedCell.getGlobalAddrs());
			for (Address bindedGlobal : bindedGlobals) {
				if (g.allGlobal.get(bindedGlobal) == mergedCell) {
					g.allGlobal.put(bindedGlobal, cell2);
					cell2.getGlobalAddrs().add(bindedGlobal);
					cell2.getParent().setGlobal(true, bindedGlobal);
					mergedCell.getGlobalAddrs().remove(bindedGlobal);
				}
			}

			if (mergedCell.getCalleeArgLabel() != null && mergedCell.getCalleeArgLabel().size() > 0) {
				for (String label : mergedCell.getCalleeArgLabel()) {
					mergedCellG.getCalleeargs().put(label, cell2);
					cell2.addCalleeArgLabel(label);
				}
				mergedCell.calleeArgLabel = null;
				if (g != mergedCellG)
					parent2.g = mergedCellG;
			}

			for (Function key : mergedCell.getStackLocs().keySet()) {
				HashSet<Integer> stackLocSet = new HashSet<Integer>();
				stackLocSet.addAll(mergedCell.getStackLocs(key));
				if (stackLocSet.size() > 0) {
					for (Integer stackLoc : stackLocSet) {
						mergedCell.getStackLocs(key).remove(stackLoc);
						cell2.addStackLocs(key, stackLoc);
						Graph curg = g.getAllLocalGraphs().get(key);
						curg.stackObj.put(stackLoc, cell2);
					}
				}
			}

			for (Function key : mergedCell.getRSPOffset().keySet()) {
				if (mergedCell.isRSP(key)) {
					int offset = mergedCell.getRSPOffset(key);
					mergedCell.setRSPOffset(key, null);
					cell2.setRSPOffset(key, offset);
				}
			}

			if (mergedCell.getInMemEdges() != null) {
				Cell inMemEdgeMerged = mergedCell.getInMemEdges();
				mergedCell.setInMemEdges(null);
				cell2.setInMemEdges(inMemEdgeMerged);
				inMemEdgeMerged.setOutMemEdge(cell2);
			}

			if (mergedCell.getOutMemEdge() != null) {
				Cell outMemEdgeMerged = mergedCell.getOutMemEdge();
				mergedCell.setOutMemEdge(null);
				cell2.setOutMemEdge(outMemEdgeMerged);
				outMemEdgeMerged.setInMemEdges(cell2);
			}

			cell2.getReadFunc().addAll(mergedCell.getReadFunc());
			cell2.getWriteFunc().addAll(mergedCell.getWriteFunc());

		}

		parent2.addMergedWith(parent1.getLoc());

		if (parent1.getConstants() != null)
			parent2.addConstants(parent1.getConstants());
		if (parent1.isArray())
			parent2.setArray(true);
		if (parent1.isOnStack()) {
			parent1.setOnStack(false);
			parent2.setOnStack(true);
		}
		if (parent2.isCollapsed())
			return parent2.get(0);
		return this;
	}

	public Graph getGraph() {
		return this.getParent().getGraph();
	}

	public void addOutEdges(Cell dst) {
		if (dst == null || this.parent == null)
			return;
		this.getParent().setIsConstant(false);
		if (outEdge == dst)
			return;
		this.getGraph().changed = true;
		dst.addInEdges(this);
		if (this.getParent().getOnHeap() && dst.getParent() != null)
			dst.getParent().setOnHeap(true);
		if (this.getParent().getIsArg() && dst.getParent() != null)
			dst.getParent().setIsArg(true);
		if (outEdge == null || outEdge.getParent() == null)
			outEdge = dst;
		else
			outEdge.merge(dst);
	}

	public void setOutEdges(Cell dst) {
		if (dst == null || this.parent == null)
			return;
		this.getParent().setIsConstant(false);

		// it is possible that a pointer is stored into a global variable when merging
		// also, we only merge with global variable if it stores a pointer
		// if (dst.getParent() != null && dst.getParent().getPossiblePointers().size() >
		// 0) {
		// HashMap<Address, Cell> allGlobals = this.getGraph().getAllGlobals();
		// for (Address maddr : this.getParent().getPossiblePointers()) {
		// if (!allGlobals.containsKey(maddr))
		// allGlobals.put(maddr, dst);
		// else {
		// Cell origin = allGlobals.get(maddr);
		// origin.merge(dst);
		// }
		// dst.getParent().setGlobal(true);
		// }
		// }

		// TODO: if this cell is pointer, need to handle the new outedge, read its
		// content from the pointer addr
		dst.addInEdges(this);
		if (this.getParent().getOnHeap() && dst.getParent() != null)
			dst.getParent().setOnHeap(true);
		if (this.getParent().getIsArg() && dst.getParent() != null)
			dst.getParent().setIsArg(true);
		if (outEdge == null)
			outEdge = dst;
		else if (outEdge == dst)
			return;
		else {
			// delete the original link
			outEdge.removeInEdges(this);
			outEdge = dst;
		}
	}

	public Cell getOutEdges() {
		return outEdge;
	}

	public void addAccessMode(String access) {
		this.accessMode.add(access);
	}

	public void addInEvEdges(Varnode v) {
		this.inEvEdges.add(v);
	}

	public HashSet<Varnode> getInEvEdges() {
		return inEvEdges;
	}

	public void addInEdges(Cell c) {
		if (c == null)
			return;
		if (c.getParent().isGlobal() && this.getParent() != null)
			this.getParent().setGlobal(true, null);
		this.inEdges.add(c);
	}

	public HashSet<Cell> getInEdges() {
		return inEdges;
	}

	public void removeInEdges(Cell dst) {
		inEdges.remove(dst);
	}

	public void removeOutEdges() {
		outEdge = null;
	}

	public int getFieldOffset() {
		return fieldOffset;
	}

	public void setFieldOffset(int fieldOffset) {
		this.fieldOffset = fieldOffset;
	}

	public DSNode getParent() {
		return parent;
	}

	public void setParent(DSNode parent) {
		this.parent = parent;
	}

	/*
	 * This is called when we know f is a function pointer, so there is no need to
	 * load its content
	 */
	public void addPointers(Address f) {
		this.possiblePointers.add(f);
		this.getParent().clearConstant();
	}

	public boolean addPointersWithLoading(Address f, HashSet<DSNode> visitedDSNode) {
		if (this.getParent() == null)
			return false;
		this.getParent().clearConstant();
		if (this.possiblePointers.contains(f))
			return false;
		this.possiblePointers.add(f);
		HashSet<Address> fp = new HashSet<Address>();
		fp.add(f);
		Program currProg = this.getGraph().getF().getProgram();
		HashSet<Address> fps = IndirectCallTargetResolving.getPossibleFuncPointer(fp, currProg);
		boolean ret = false;
		// if this node is global, when adding new pointer values, need to check its
		// related callsites
		if (this.getParent() != null) {
			for (Pair<CallSiteNode, String> cs : this.getInCallSite()) {
				CallSiteNode csn = cs.getK();
				String s = cs.getV();
				if (s == "func" && f != null && csn.isIndirect) {
					if (fps.size() > 0) {
						Instruction instr = currProg.getListing().getInstructionAt(csn.getLoc());
						if (instr != null) {
							for (Address address : fps) {
								csn.getGraph().resolvedNewCallSite = true;
								int as = currProg.getAddressFactory().getAddressSpace("ram").getSpaceID();
								address = currProg.getAddressFactory().getAddress(as, address.getOffset());
								instr.addOperandReference(0, address, RefType.COMPUTED_CALL, SourceType.USER_DEFINED);
							}
						}
						DebugUtil.print("Solved " + csn.toString() + " -> " + fps.toString());

						BufferedWriter out;
						try {
							out = new BufferedWriter(new OutputStreamWriter(
									new FileOutputStream(IndirectCallTargetResolving.outPath, true)));
							out.write(csn.toString() + "@1" + "@" + fps.toString());
							out.newLine();
							out.close();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						ret |= true;
					}
				}
			}
		}
		// during merging, if a pointer of global is added to this cell, need to load
		// and check the content in global

		// load the content from f, store it into outedges's possible pointer, set the
		// outedge to global variable
		if (fps.contains(f))
			return ret;
		else if (this.getParent() != null) {
			Graph thisgraph = this.getGraph();
			if (!thisgraph.allGlobal.containsKey(f)) {
				ret |= IndirectCallTargetResolving.loadGlobalVariable(f, this, thisgraph.getF().getProgram(),
						thisgraph.allGlobal, this.getGraph(), visitedDSNode);
			} else {
				Cell origin = thisgraph.allGlobal.get(f);
				this.loadGlobalVarsToThisPtr(f, visitedDSNode);
				if (origin.possiblePointers.size() > 0)
					ret |= true;
			}
			// the value stored in address f is out, put it into the memory
		}
		return ret;

	}

	public void addAllPointers(HashSet<Address> f) {
		if (this.getParent() == null)
			return;
		this.getParent().clearConstant();
		for (Address addr : f) {
			this.addPointers(addr);
		}
	}

	public void loadGlobalVarsToThisPtr(Address f, HashSet<DSNode> visitedDSNode) {
		if (this.getParent() == null)
			return;
		DSNode thisParent = this.getParent();
		Graph thisgraph = this.getGraph();
		Cell origin = thisgraph.allGlobal.get(f);
		Cell inEdgeOrigin = origin.getInMemEdges();
		if (inEdgeOrigin != null && inEdgeOrigin.getParent() != null) {
			int offsetOrigin = inEdgeOrigin.fieldOffset;
			int offsetThis = this.fieldOffset;
			HashSet<Integer> keyset = new HashSet<Integer>();
			keyset.addAll(inEdgeOrigin.getParent().members.keySet());
			DSNode originParent = inEdgeOrigin.getParent();
			for (int offset : keyset) {
				Cell cellOrigin = originParent.get(offset);
				Cell cellThis = thisParent.getOrCreateCell(offset - offsetOrigin + offsetThis);
				if (cellOrigin == null || cellOrigin.getOutEdges() == null || cellThis == null)
					continue;
				Cell out = cellThis.getOutEdges();
				Cell originOut = cellOrigin.getOutEdges();
				if (out != null && out.getParent() != null) {
					if (visitedDSNode != null)
						out.merge(originOut, visitedDSNode);
					else
						out.merge(originOut);
				} else {
					cellThis.setOutEdges(originOut);
				}
			}
		} else {
			Cell out = this.getOutEdges();
			if (out != null && out.getParent() != null) {
				if (visitedDSNode != null)
					out.merge(origin, visitedDSNode);
				else
					out.merge(origin);
			} else {
				this.setOutEdges(origin);
			}
		}
	}

	public HashSet<Address> getPossiblePointers() {
		if (this.getParent() == null)
			return new HashSet<Address>();

		return this.possiblePointers;
	}

	public HashSet<Address> getPossiblePointersWithLoading(HashSet<DSNode> visitedDSNode) {
		if (this.getParent() == null)
			return new HashSet<Address>();
		int minkey = this.getParent().getMinOffset();
		// if (this.possiblePointers.size() > 0 || this.getFieldOffset() == minkey)
		return this.possiblePointers;

		// if (this.getParent().get(minkey) != null) {
		// HashSet<Address> ptrs = new HashSet<Address>();
		// ptrs.addAll(this.getParent().get(minkey).possiblePointers);
		// if (ptrs.size() == 0)
		// return ptrs;
		//
		// for (Address ptr : ptrs) {
		// try {
		// this.addPointersWithLoading(ptr.add(this.getFieldOffset() - minkey),
		// visitedDSNode);
		// } catch (ghidra.program.model.address.AddressOutOfBoundsException e) {
		// DebugUtil.print("Address out of bounds");
		// }
		// }
		// return this.possiblePointers;
		// }
		// return new HashSet<Address>();
	}

	public String toString() {
		String s = "Cell@offset" + String.valueOf(fieldOffset) + "\n";
		if (this.getGlobalAddrs().size() > 0)
			s += this.getGlobalAddrs().toString() + "\n";
		if (this.parent != null)
			s += this.parent.toString();
		return s;
	}

}

class AllocSite {
	private Varnode definedVar;
	private ArrayList<Function> callpath;

	public AllocSite() {
		this.callpath = new ArrayList<Function>();
	}

	public AllocSite(Varnode v, Function f) {
		this.definedVar = v;
		this.callpath = new ArrayList<Function>();
		callpath.add(f);
	}

	public Varnode getDefinedVar() {
		return definedVar;
	}

	public void setDefinedVar(Varnode definedVar) {
		this.definedVar = definedVar;
	}

	public ArrayList<Function> getCallpath() {
		return callpath;
	}

	public void addCallpath(Function f) {
		this.callpath.add(f);
	}

	public AllocSite deepcopy() {
		AllocSite as = new AllocSite();
		as.setDefinedVar(this.definedVar);
		for (Function f : this.callpath)
			as.addCallpath(f);
		return as;
	}
}

class Pair<K, V> {
	private final K element0;
	private final V element1;

	public Pair(K element0, V element1) {
		this.element0 = element0;
		this.element1 = element1;
	}

	public K getK() {
		return element0;
	}

	public V getV() {
		return element1;
	}

	public String toString() {
		return element0.toString();
	}
}
