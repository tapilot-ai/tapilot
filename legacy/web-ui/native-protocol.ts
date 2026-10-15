export interface NativeHttpRequest {
  url: string;
  method: string;
  headers: Record<string, string>;
  bodyBase64?: string;
}

export interface NativeHttpResponse {
  status: number;
  headers: Record<string, string>;
  bodyBase64: string;
}

export interface NativeNodeRuntimeDoctorResult {
  node: string;
  platform: string;
  arch: string;
  runtimeVersion: string;
  testRunnerLoaded: boolean;
}

export interface NativeNodeTestRunRequest {
  source: string;
  kind: 'task' | 'yaml';
  environment: Record<string, string>;
}

export interface NativeReportDescriptor {
  reportId: string;
  kind: 'agent' | 'test-runner';
  title: string;
  projectName?: string;
  caseName?: string;
  attempt?: number;
}

export interface NativeNodeTestRunResult {
  exitCode: number;
  reportId: string;
  reports: NativeReportDescriptor[];
  output: string[];
  finalResponse?: string;
  failure?: string;
}

export type NativeAppLanguage = 'system' | 'zh' | 'en';
export type NativeAppTheme = 'system' | 'light' | 'dark';

export type NativeTaskStatus =
  | 'starting'
  | 'running'
  | 'finalizing'
  | 'stopping'
  | 'waiting_user'
  | 'succeeded'
  | 'failed'
  | 'stopped';
