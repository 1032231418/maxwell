require 'yaml'
DATA_DIR=File.expand_path(File.dirname(__FILE__) + "/../data")

class Schema
  def initialize(connection, db_name, schema=nil)
    @db = db_name
    @cx = connection
    @schema = schema
  end

  def fetch
    return @schema if @schema

    @schema = {}

    binlog_info

    tables = @cx.query("show tables from #{@db}")
    tables.each do |row|
      table = row.values.first
      @schema[table] = []

      cols = @cx.query("select * from information_schema.columns where TABLE_SCHEMA='#{@db}' and TABLE_NAME='#{table}' ORDER BY ORDINAL_POSITION")
      cols.each do |col|
        c = col.inject({}) do |accum, array|
          k, v = *array
          k = k.downcase.to_sym
          next accum if [:table_catalog, :table_schema, :table_name, :column_key, :extra, :privileges, :column_comment].include?(k)
          accum[k] = v
          accum
        end
        @schema[table] << c
      end
    end
    @schema
  end

  def binlog_info
    @binlog_info ||= begin
      res = @cx.query("SHOW MASTER STATUS")
      row = res.first
      {file: row['File'], pos: row['Position']}
    end
  end

  def save
    fetch
    fname = self.class.schema_fname(@db, @binlog_info[:file], @binlog_info[:pos])
    File.open(fname, "w+") do |f|
      f.write(@schema.to_yaml)
    end
  end

  def self.schema_fname(db, logfile, pos)
    DATA_DIR + "/" + [db, logfile, pos].join('-') + ".yaml"
  end

  def self.load(db, logfile, pos)
    fname = schema_fname(db, logfile, pos)
    return nil unless File.exist?(fname)
    schema = YAML.load(File.read(fname))
    new(nil, db, schema)
  end
end
