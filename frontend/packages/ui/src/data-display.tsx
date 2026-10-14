import { Descriptions, Table } from 'antd';
import type { ReactNode } from 'react';

export interface DataColumn<Row> {
  key: string;
  title: string;
  render: (row: Row) => ReactNode;
  width?: number;
}

interface DataTableProps<Row> {
  columns: readonly DataColumn<Row>[];
  rows: readonly Row[];
  rowKey: (row: Row) => string;
  loading?: boolean;
  emptyText?: string;
  defaultExpandAll?: boolean;
}

export function DataTable<Row extends object>({ columns, rows, rowKey, loading = false, emptyText = '暂无数据', defaultExpandAll = false }: DataTableProps<Row>) {
  return (
    <Table<Row>
      size="middle"
      pagination={false}
      loading={loading}
      dataSource={[...rows]}
      rowKey={rowKey}
      locale={{ emptyText }}
      expandable={{ defaultExpandAllRows: defaultExpandAll }}
      columns={columns.map((column) => ({
        key: column.key,
        title: column.title,
        ...(column.width === undefined ? {} : { width: column.width }),
        render: (_: unknown, row: Row) => column.render(row),
      }))}
    />
  );
}

export function DescriptionList({ items }: { items: readonly { label: string; value: ReactNode }[] }) {
  return (
    <Descriptions bordered size="small" column={1}>
      {items.map((item) => <Descriptions.Item key={item.label} label={item.label}>{item.value}</Descriptions.Item>)}
    </Descriptions>
  );
}
