import { Request, Response } from 'express';
import { get_function, add_function } from '../database';
import { exec, spawn } from "child_process";
import { UploadedFile } from 'express-fileupload';
import { CODE_DIFF_UPLOAD_DIR, UPLOAD_DIR } from '../config';
import * as fs from 'fs';
import * as path from 'path';
import session from 'express-session';

// const fs = require('fs');
// const path = require('path');
// Define a constant for the location of the JSON history file
const HISTORY_JSON_PATH: string = `${CODE_DIFF_UPLOAD_DIR}/upload_history.json`;


// Function to read the JSON file
const readJsonFile = (filepath: string): any => {
    const rawData = fs.readFileSync(filepath, 'utf8');
    return JSON.parse(rawData);
}

// Function to write to the JSON file
const writeJsonFile = (filepath: string, data: any): void => {
    const content = JSON.stringify(data, null, 4);
    fs.writeFileSync(filepath, content);
}

const initializeJsonFile = (filepath: string): void => {
    if (!fs.existsSync(filepath)) {
        fs.writeFileSync(filepath, JSON.stringify([], null, 4));
    }
}

// Function to add a new upload record for file pairs
// const addUploadRecord = (
//     fileName1: string,
//     fileName2: string,
//     projectName1: string,
//     projectName2: string
// ): void => {
//     const combinedDirectoryName = `${fileName1}_and_${fileName2}`;
//     const history = readJsonFile(HISTORY_JSON_PATH);
//     const timestamp = new Date().toISOString();
//     const newRecord = {
//         directoryName: combinedDirectoryName,
//         file1: {
//             projectName: projectName1
//         },
//         file2: {
//             projectName: projectName2
//         },
//         timestamp: timestamp
//     };
//     console.log('[addUploadRecord] newRecord:' + newRecord)
//     history.push(newRecord);
//     writeJsonFile(HISTORY_JSON_PATH, history);
// }
const addUploadRecord = (
    fileName1: string,
    fileName2: string,
    projectName1: string,
    projectName2: string
): void => {
    const combinedDirectoryName = `${fileName1}_and_${fileName2}`;
    const history = readJsonFile(HISTORY_JSON_PATH);
    const timestamp = new Date().toISOString();

    // 查找是否已经存在一个与 combinedDirectoryName 匹配的记录
    const existingRecord = history.find(record => record.directoryName === combinedDirectoryName);

    if (existingRecord) {
        // 如果记录存在，更新它的 timestamp
        existingRecord.timestamp = timestamp;
    } else {
        // 否则，创建一个新记录并将其推入 history 数组
        const newRecord = {
            directoryName: combinedDirectoryName,
            file1: {
                projectName: projectName1
            },
            file2: {
                projectName: projectName2
            },
            timestamp: timestamp
        };
        console.log('[addUploadRecord] newRecord:', newRecord);
        history.push(newRecord);
    }

    writeJsonFile(HISTORY_JSON_PATH, history);
}

const currentDir = path.resolve(__dirname);  // currentDir = /Users/.../sigmadiff/deepdiweb/proxy/src/routes
const rootDir = path.dirname(currentDir) + '/';  // root directory = /Users/.../sigmadiff/deepdiweb/proxy/src/
// console.debug('rootDir: ' + rootDir);
const sigmadiffDir = 'SigmaDiff/'

function execShellCommand(cmd: any, name: string) {
    return new Promise((resolve, reject) => {
        // // Check real-time logs or outputs of the command
        // const command = spawn(cmd, [origin_file], { shell: true });
        // // Print the real-time output of the command
        // command.stdout.on('data', data => {
        //     console.log(`stdout: ${data}`);
        // });
        //
        // command.stderr.on('data', data => {
        //     console.error(`stderr: ${data}`);
        // });
        //
        // command.on('close', code => {
        //     if (code === 0) {
        //         resolve(origin_file);
        //     } else {
        //         reject(new Error(`command exited with code ${code}`));
        //     }
        // });

        // set buffer size to 10MB to avoid maxBufferSize exceed error
        exec(cmd, { maxBuffer: 10 * 1024 * 1024 }, (error: any, stdout: string, stderr: any) => {
            if (error) {
                console.warn(error);
                reject(error);
            } else {
                resolve(name);
            }
        });
    });
}

async function runSigmaDiff(req: Request, res: Response) {
    try {
        const { origin_file, compared_file } = req.body;

        let f1 = `${rootDir}/${sigmadiffDir}data/origin_file/${origin_file}`
        let f2 = `${rootDir}/${sigmadiffDir}data/compared_file/${compared_file}`
        let ghidra_home = `${rootDir}/ghidra_10.2.2_PUBLIC`
        let output = `${rootDir}${sigmadiffDir}out/${origin_file}_and_${compared_file}`
        let command = `cd src/SigmaDiff/ && python3 sigmadiff.py --input1 ${f1} --input2 ${f2} --ghidra_home ${ghidra_home} --output_dir ${output}`
        console.log('[runSigmaDiff] ' + command)
        execShellCommand(command, origin_file)
            .then((origin_file) => {
                console.log('finished sigmadiff')
                let result = 'good'
                res.status(200).json({ result });
            })
            .catch((e) => {
                console.log('[runSigmaDiff] failed: ' + e.message);
                res.status(400).send('An error occured while trying to process the code diff.');
            })
    } catch (ex) {
        console.error(`An error occured while trying to code diff: ${ex}`);
        res.status(400).send('An error occured while trying to process the code diff.');
    }
}

async function getCodeDiffResult(req: Request, res: Response) {
    try {
        // get two file name and run example_out
        const { name, diffItem } = req.body; // function_name
        // const dirname = `${diffItem.file1.projectName}_and_${diffItem.file2.projectName}`
        const target = get_function(name);
        console.log('name: ' + name + ', target: ' + target);
        const sigmaDiffOutDir = `${rootDir}/SigmaDiff/out/`;
        let latestModifiedDir = null;

        // 如果 diffItem 为 null 或未定义
        if (!diffItem) {
            const directories = fs.readdirSync(sigmaDiffOutDir, { withFileTypes: true })
                .filter(dirent => dirent.isDirectory())
                .map(dirent => ({
                    name: dirent.name,
                    time: fs.statSync(path.join(sigmaDiffOutDir, dirent.name)).mtime.getTime()
                }))
                .sort((a, b) => b.time - a.time);  // 按时间戳排序，最新的目录在前

            latestModifiedDir = directories[0].name;  // 获取最新修改的目录名

            // 现在你可以使用 latestModifiedDir
            // ...

        } else {
            // 使用 diffItem
            latestModifiedDir = `${diffItem.file1.projectName}_and_${diffItem.file2.projectName}`;
        }

        const currentDir = path.dirname(__filename);
        console.log('currentDir: ' + currentDir);

        if (name && target) {
            const folder1 = `${diffItem.file1.projectName}`;
            const folder2 = `${diffItem.file2.projectName}`;  // ${latestModifiedDir}/
            const command = `cd ${sigmaDiffOutDir} && python3 evaluate_token.py -f ${name} ${target} -n ${folder1} ${folder2}` // -d ${rootDir}/SigmaDiff/out

            execShellCommand(command, name)
                .then((name) => {
                    const fileName: string = `${sigmaDiffOutDir}/${latestModifiedDir}/${name}.html`;
                    let result = fs.readFileSync(fileName, 'utf8');
                    res.status(200).json({ result })
                })
                .catch((e) => {
                    console.log(e)
                    res.status(400).send('getCodeDiffResult: An error occured while executing evaluate_token.py.');
                })
        } else {
            res.status(400).send('getCodeDiffResult: An error occured while trying to process the code diff.');
        }

        // remove any files past our upload limit
    }
    catch (ex) {
        console.error(`An error occured while trying to code diff: ${ex}`);
        res.status(400).send('An error occured while trying to process the code diff.');
    }
}

async function readFunctions(req: Request, res: Response) {
    try {
        const filename1 = req.query.filename1 as string;
        const filename2 = req.query.filename2 as string;
        // const { filename1, filename2 } = req.body; // Assuming filenames are sent in the request body

        const targetDirectory = `${filename1}_vs_${filename2}`;
        const source = `src/SigmaDiff/out/${filename1}_and_${filename2}/`
        const getDirectories = fs.readdirSync(source, { withFileTypes: true })
            .filter(function (dirent) {
                if (dirent.isDirectory() && dirent.name.includes(targetDirectory) && !dirent.name.includes('Pretrain')) {
                    return true;
                }
                return false;
            })
            .map(dirent => dirent.name)

        const fileName: string = `${source}/${getDirectories[0]}/matched_functions.txt`;
        const readline = require('readline');
        let fileStream = fs.createReadStream(fileName)

        const rl = readline.createInterface({
            input: fileStream,
            crlfDelay: Infinity
        });
        let functions: string[] = []
        for await (const line of rl) {
            const words = line.split(" ")
            add_function(words[0], words[1])
            functions.push(words[0])
        }
        res.status(200).json({ functions })
    }
    catch (ex) {
        console.error(`CodeDiff: An error occured while trying to read functions: ${ex}`);
        res.status(400).send('CodeDiff: An error occured while trying to read functions.');
    }
}

async function getJsonFromBinary(req: Request, res: Response) {
    try {
        let name = req.query.name as string;
        console.log(name)

        let bindir = `${rootDir}webportal/spec2006x86/O2/${name}`
        let outdir = `${rootDir}webportal/spec2006x86/O2_out`
        let ghidradir = `${rootDir}ghidra_10.2.2_PUBLIC`
        let scriptdir = `${rootDir}webportal/ghidra_scripts`
        let projdir = `${rootDir}webportal/ghidra`
        let decompdir = `${rootDir}webportal/spec2006x86/decompiled`
        let command = `cd src/webportal/ && python run.py --name ${name} --bindir ${bindir} --outdir ${outdir} --ghidradir ${ghidradir} --scriptdir ${scriptdir} --projdir ${projdir} --decompdir ${decompdir}`
        exec(command,
            function (error, stdout, stderr) {
                console.log('stdout: ' + stdout);
                console.log('stderr: ' + stderr);
                if (error !== null) {
                    console.log('exec error: ' + error);
                } else {
                    console.log("Finished processing all binaries.");
                    const fileName: string = `${rootDir}webportal/spec2006x86/O2_out/${name}/${name}.json`
                    const content = readJsonFile(fileName);
                    res.status(200).json(content);
                }
            });
    } catch (ex) {
        console.error(`An error occured while trying to decompile: ${ex}`);
        res.status(400).send('An error occured while trying to process the binary.');
    }
}

async function readScript(req: Request, res: Response) {
    initializeJsonFile(HISTORY_JSON_PATH);

    const fileName: string = `src/example_output/script.txt`;
    let script_result = fs.readFileSync(fileName, 'utf8');

    // if ((req.session as any).views) {
    //     (req.session as any).views++;
    //     console.log('[server] add session')
    //     // res.send(`<p>Number of views: ${req.session.views}</p>`);
    // } else {
    //     (req.session as any).views = 1;
    //     console.log('[server] add session')
    //     // res.send('Welcome! Refresh the page to see the view count.');
    // }
    //
    // console.log('[server] session view: ' + (req.session as any).views)
    res.status(200).json({ script_result })
}

let firstFileGlobal: { fileName: string, projectName: string } | null = null;

function ensureDirectoryExistence(directory) {
    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, { recursive: true });
    }
}

export default async function codeDiffUpload(req: Request, res: Response) {
    try {
        const { order, project_name } = req.body;
        // {
        //     "project_name": "FUN_00002b90.c",
        //     "arch": "detect",
        //     "mode": "undefined",
        //     "order": "0"
        // }

        const file = req.files?.filedata as UploadedFile;
        if (!file) {
            res.status(400).send('no file provided');
            return;
        }
        const file_name = path.basename(file.name);
        const file_name_no_ext = path.parse(project_name).name;
        const file_path_zoom = `${CODE_DIFF_UPLOAD_DIR}/now/origin_file/${project_name}`;
        const file_path_tur = `${CODE_DIFF_UPLOAD_DIR}/now/compared_file/${project_name}`;
        const file_path = `${UPLOAD_DIR}${project_name}`;
        ensureDirectoryExistence(`${CODE_DIFF_UPLOAD_DIR}/now/origin_file`);
        ensureDirectoryExistence(`${CODE_DIFF_UPLOAD_DIR}/now/compared_file`);

        console.log('[codeDiffUpload] project_name: ' + project_name)
        console.log('[codeDiffUpload] file_path: ' + file_path + ', file_path_zoom: ' + file_path_zoom + ', file_path_tur: ' + file_path_tur)
        console.log('[codeDiffUpload] file_name: ' + file_name + ', file_name_no_ext: ' + file_name_no_ext)

        await fs.promises.rename(file.tempFilePath, file_path);

        if (Number(order) === 0) {
            fs.copyFile(file_path, file_path_zoom, function (err) {
                if (err) {
                    console.log(err)
                }
                res.end();
            })
        }
        else if (Number(order) === 1) {
            fs.copyFile(file_path, file_path_tur, function (err) {
                if (err) {
                    console.log(err)
                }
                res.end();
            })
        }

        // If this is the first file being uploaded
        // if (req.session && !(req.session as any).firstFile) {
        //     (req.session as any).firstFile = {
        //         fileName: file_name,
        //         projectName: project_name
        //     };
        //     console.log('[codeDiffUpload] session:' + (req.session as any).firstFile)
        // } else {
        //     console.log('[codeDiffUpload] session: no update for upload files.' + req.session)
        // }
        //
        // if (req.session && (req.session as any).firstFile) {
        //     const firstFileName = (req.session as any).firstFile.fileName;
        //     const firstProjectName = (req.session as any).firstFile.projectName;
        //
        //     const secondFileName = file_name;
        //     const secondProjectName = project_name;
        //
        //     // Call addUploadRecord with the retrieved and current file details
        //     console.log('[codeDiffUpload] session:' + (req.session as any).firstFile)
        //     addUploadRecord(firstFileName, secondFileName, firstProjectName, secondProjectName);
        //
        //     // Clear the session for next pair of files
        //     (req.session as any).firstFile = null;
        // } else {
        //     console.log('[codeDiffUpload] session: no update for upload files.')
        // }

        // If this is the first file being uploaded
        if (Number(order) === 0) { // !firstFileGlobal ||
            firstFileGlobal = {
                fileName: file_name_no_ext,
                projectName: project_name
            };
            console.log('[codeDiffUpload] firstFileGlobal:', firstFileGlobal);
        } else if (Number(order) === 1) {
            const firstFileName = firstFileGlobal.fileName;
            const firstProjectName = firstFileGlobal.projectName;

            const secondFileName = file_name_no_ext;
            const secondProjectName = project_name;

            // Call addUploadRecord with the retrieved and current file details
            console.log('[codeDiffUpload] firstFileGlobal:', firstFileGlobal);
            addUploadRecord(firstFileName, secondFileName, firstProjectName, secondProjectName);

            // Clear the firstFileGlobal for next pair of files
            firstFileGlobal = null;

            const currentDir = `${CODE_DIFF_UPLOAD_DIR}/now`;
            const newDir = `${CODE_DIFF_UPLOAD_DIR}/${firstFileName}_and_${secondFileName}`;
            if (fs.existsSync(newDir)) {
                fs.rmdirSync(newDir, { recursive: true });  // exist -> delete
            }
            fs.rename(currentDir, newDir, (err) => {
                if (err) {
                    console.error('Error renaming directory:', err);
                } else {
                    console.log('Directory successfully renamed');
                }
            });

        }

        let shortName = 'codeDiffUpload'
        res.status(200).json({ shortName });

        // remove any files past our upload limit
        //await clear_cache_if_over();
    }
    catch (ex) {
        console.error(`codeDiffUpload: An error occured while trying to upload a file: ${ex}`);
        res.status(400).send('codeDiffUpload: An error occured while trying to process the file upload.');
    }
}

export const getJsonContent = (req: Request, res: Response): void => {
    try {
        // Ensure the JSON file is initialized
        initializeJsonFile(HISTORY_JSON_PATH);

        // Read the content
        const content = readJsonFile(HISTORY_JSON_PATH);
        console.log('[getJsonContent] content:', content);

        // Send the content as response
        res.json(content);
    } catch (error) {
        console.error("Error reading JSON content:", error);
        res.status(500).send("Internal Server Error");
    }
};

export { readFunctions, getCodeDiffResult, readScript, runSigmaDiff, codeDiffUpload, initializeJsonFile, getJsonFromBinary }