import process, { exit } from 'process';
import os from 'os';
import fs from 'fs';


export const UPLOAD_DIR = process.env.UPLOAD_DIR || `${os.tmpdir()}/cache/`;
export const CODE_DIFF_UPLOAD_DIR = `src/SigmaDiff/data`
export const MAX_PROJECTS_CACHED = Number.parseInt(process.env.MAX_PROJECTS_CACHED || '50');
if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR);
}
// export const DEEPDI_URL = process.env.DEEPDI_URL || '';
// if (DEEPDI_URL === '') {
//     console.error('DEEPDI_URL is not set, exiting.');
//     exit(1);
// }