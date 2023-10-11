import { MAX_PROJECTS_CACHED } from './config';

export const Projects: Map<string, ProjectInfo> = new Map();
export const FunctionList: Map<string, string> = new Map();

export function add_project(key: string, info: ProjectInfo) {
    Projects.set(key, info);

    // only keep a maximum of MAX_PROJECTS_CACHED
    const projects_to_remove = Projects.size - MAX_PROJECTS_CACHED;  // memory management: remove old project if overload
    if (projects_to_remove > 0) {
        const projects_names_to_remove = Array.from(Projects.keys()).slice(0, projects_to_remove);
        for (const name of projects_names_to_remove) {
            Projects.delete(name);
        }
    }
    Projects.forEach((value: ProjectInfo, key: string) => {
        console.log('Projects.value: ' + value.project_name)
    })
}

export function add_function(key:string, value: string) {
    FunctionList.set(key,value);
}

export function get_function(key:string) {
    return FunctionList.get(key);
}

export function get_project(key: string): ProjectInfo | undefined {
    return Projects.get(key);
}

export function delete_project(key: string): boolean {
    return Projects.delete(key);
}

interface ProjectInfo {
    project_name: string;
    file_path: string; // the full path to the saved location

    raw: boolean; // whether or not we have raw bytes vs an actual file
    // only set if raw is true
    arch?: string;
    mode?: string;
}
