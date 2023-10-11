import { UPLOAD_DIR } from './config';
import cors from 'cors';
import express from 'express';
import fileUpload from 'express-fileupload';
import path from 'path';
import router from './routes';
// import * as session from 'express-session';
import session from 'express-session';


const server = express();

server.use((req, res, next) => {
    console.log(`[${(new Date()).toISOString()}] ${req.method} ${req.url} (${res.statusCode})`);
    next();
});

server.use(express.json({ limit: '1mb' }));
// server.use(express.urlencoded({ limit: '200mb' }));
// server.use(express.json());
server.use(fileUpload({
    safeFileNames: true,
    useTempFiles: true,
    tempFileDir: UPLOAD_DIR
}));
server.use(express.static(path.join(__dirname, 'frontend')));
server.use(cors());

server.use('/odaweb/', router);

// import crypto from 'crypto';
// function generateSecret(length: number = 256): string {
//     return crypto.randomBytes(length).toString('hex');
// }

// const sessionMiddleware = session as any;
server.use(session({
    // secret: generateSecret(), // unsafe
    secret: 'your-secret-key', // unsafe
    resave: false,
    saveUninitialized: true,
    cookie: {
        httpOnly: true,
        secure: false, // 设为 false，除非你用 HTTPS
        maxAge: 60000 // 例如，1分钟。你可以根据需要调整。
    }
}));

server.listen(8001, () => {
    console.log('Server is ready to accept requests!');
});

// import { initializeJsonFile } from './routes/codediff';
// import { CODE_DIFF_UPLOAD_DIR } from './config';
// const HISTORY_JSON_PATH: string = `${CODE_DIFF_UPLOAD_DIR}/upload_history.json`;
// initializeJsonFile(HISTORY_JSON_PATH);
