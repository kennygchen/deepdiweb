declare module 'express-session' {
    export interface SessionData {
        views?: number;
        firstFile: {
            fileName: string;
            projectName: string;
        } | null;
    }
}
